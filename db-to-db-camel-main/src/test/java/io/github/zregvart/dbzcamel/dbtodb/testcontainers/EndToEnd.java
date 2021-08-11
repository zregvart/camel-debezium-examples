/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.zregvart.dbzcamel.dbtodb.testcontainers;

import static configuration.Async.newCompletableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.cucumber.java8.En;
import io.cucumber.java8.HookBody;
import io.cucumber.java8.StepDefinitionBody;
import io.github.zregvart.dbzcamel.dbtodb.App;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.main.BaseMainSupport;
import org.apache.camel.main.MainListenerSupport;
import org.apache.camel.spi.RouteController;
import org.approvaltests.Approvals;
import org.approvaltests.core.Options;
import org.approvaltests.core.Scrubber;
import org.approvaltests.namer.ApprovalNamer;
import org.approvaltests.namer.NamedEnvironment;
import org.approvaltests.namer.NamerFactory;
import org.approvaltests.namer.NamerWrapper;
import org.approvaltests.scrubbers.RegExScrubber;
import org.approvaltests.scrubbers.Scrubbers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spun.util.persistence.Loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import configuration.Async;
import configuration.LifecycleSupport;
import data.Customer;
import database.MySQLDestinationDatabase;
import database.PostgreSQLSourceDatabase;
import debezium.Debezium;
import kafka.Kafka;

public final class EndToEnd implements En {

	private static CompletableFuture<BaseMainSupport> camel = newCompletableFuture();

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final class GatherPayloads extends EndpointRouteBuilder {
		private final String bootstrapServers;

		private CamelContext camelContext;

		private final String id;

		private final List<Payload> payloads;

		private GatherPayloads(final List<Payload> payloads, final String boostrapServers) {
			this.payloads = payloads;
			bootstrapServers = boostrapServers;
			id = UUID.randomUUID().toString();
		}

		@Override
		public void configure() throws Exception {
			from(kafka("source.public.customers").brokers(bootstrapServers).clientId("approval-test-" + id))
				.routeId(id)
				.process(exchange -> {
					final Message message = exchange.getMessage();
					final String payload = message.getBody(String.class);
					payloads.add(new Payload(message.getHeaders(), MAPPER.readTree(payload)));
				});

			camelContext = getContext();
		}

		void stop() {
			if (camelContext == null) {
				return;
			}

			try {
				@SuppressWarnings("resource")
				final RouteController routeController = camelContext.getRouteController();
				routeController.stopRoute(id);
				camelContext.removeRoute(id);
			} catch (final Exception e) {
				throw new IllegalStateException(e);
			}
		}
	}

	@JsonPropertyOrder({"headers", "body"})
	private static class Payload {
		@JsonProperty
		final JsonNode body;

		@JsonProperty
		final Map<String, Object> headers;

		public Payload(final Map<String, Object> headers, final JsonNode body) {
			this.headers = headers;
			this.body = body;
		}
	}

	public EndToEnd(final PostgreSQLSourceDatabase postgresql, final MySQLDestinationDatabase mysql, final Debezium debezium, final Kafka kafka) {
		final List<Payload> payloads = new ArrayList<>();
		final GatherPayloads gatherPayloads = new GatherPayloads(payloads, kafka.getBootstrapServers());

		Given("a running example", () -> {
			camel.completeAsync(() -> runExample(postgresql, mysql, kafka, debezium))
				.thenAccept(example -> setupPayloadGathering(example, gatherPayloads))
				.get();
		});

		Given("A row present in the source database", postgresql::store);

		When("A row is inserted in the source database", postgresql::store);

		final StepDefinitionBody.A1<Customer> assertRowPresent = (final Customer customer) -> {
			await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
				final Optional<Customer> loaded = mysql.load(customer.id);
				assertThat(loaded).contains(customer);
			});
		};

		Then("a row is present in the destination database", assertRowPresent);

		When("A row is updated in the source database", postgresql::update);

		Then("an existing row is updated in the destination database", assertRowPresent);

		After(gatherPayloads::stop);
		After(approvalTest(payloads));
	}

	static HookBody approvalTest(final List<Payload> payloads) {
		return scenario -> {
			final Loader<ApprovalNamer> initial = Approvals.namerCreater;

			Approvals.namerCreater = () -> {
				return new NamerWrapper(scenario::getName, () -> "test-harness/src/main/resources/features");
			};

			try (NamedEnvironment env = NamerFactory.withParameters(scenario.getName())) {
				Approvals.verifyJson(MAPPER.writeValueAsString(payloads), new Options().withScrubber(scrubber()));
			} finally {
				Approvals.namerCreater = initial;
				payloads.clear();
			}
		};
	}

	static BaseMainSupport runExample(final PostgreSQLSourceDatabase postgresql, final MySQLDestinationDatabase mysql, final Kafka kafka,
		final Debezium debezium) {
		App.main.bind("app.dataSource", mysql.dataSource());

		App.main.addInitialProperty("kafka.bootstrapServers", kafka.getBootstrapServers());

		App.main.addMainListener(new MainListenerSupport() {
			@Override
			public void afterStart(final BaseMainSupport main) {
				camel.complete(App.main);
				LifecycleSupport.registerFinisher(App.main::stop);
			}
		});

		CompletableFuture.runAsync(App::main, Async.EXECUTOR)
			.handle((v, t) -> camel.completeExceptionally(t));

		try {
			return camel.thenApply(c -> {
				debezium.startConnectorFor(postgresql);
				return c;
			}).get();
		} catch (InterruptedException | ExecutionException e) {
			throw new IllegalStateException(e);
		}
	}

	static void setupPayloadGathering(final BaseMainSupport example, final GatherPayloads gatherPayloads) {
		try {
			@SuppressWarnings("resource")
			final CamelContext camelContext = example.getCamelContext();
			camelContext.addRoutes(gatherPayloads);
		} catch (final Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static Scrubber scrubber() {
		return Scrubbers.scrubAll(
			new RegExScrubber("\"ts_ms\": [0-9]{13}", "\"ts_ms\": 872835240000"),
			new RegExScrubber("\"CamelMessageTimestamp\": [0-9]{13}", "\"CamelMessageTimestamp\": 872835240000"),
			new RegExScrubber("\"kafka.TIMESTAMP\": [0-9]{13}", "\"kafka.TIMESTAMP\": 872835240000"),
			new RegExScrubber("\"sequence\": .*,", "\"sequence\": \"[]\","),
			new RegExScrubber("\"txId\": [0-9]+", "\"txId\": 1"),
			new RegExScrubber("\"lsn\": [0-9]+", "\"lsn\": 1"));
	}
}
