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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.cucumber.java8.En;
import io.cucumber.java8.HookBody;
import io.github.zregvart.dbzcamel.dbtodb.App;

import org.apache.camel.Message;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.main.BaseMainSupport;
import org.apache.camel.main.MainConfigurationProperties;
import org.apache.camel.main.MainListenerSupport;
import org.approvaltests.Approvals;
import org.approvaltests.core.Options;
import org.approvaltests.core.Scrubber;
import org.approvaltests.namer.ApprovalNamer;
import org.approvaltests.namer.NamedEnvironment;
import org.approvaltests.namer.NamerFactory;
import org.approvaltests.namer.NamerWrapper;
import org.approvaltests.scrubbers.RegExScrubber;
import org.approvaltests.scrubbers.Scrubbers;

import com.spun.util.persistence.Loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import configuration.LifecycleSupport;
import data.Customer;
import database.MySQLDestinationDatabase;
import database.PostgreSQLSourceDatabase;
import debezium.Debezium;
import kafka.Kafka;

public final class EndToEnd implements En {

	private static CompletableFuture<BaseMainSupport> camel = new CompletableFuture<>();

	public EndToEnd(final PostgreSQLSourceDatabase postgresql, final MySQLDestinationDatabase mysql, final Debezium debezium, final Kafka kafka) {
		final List<String> payloads = new CopyOnWriteArrayList<>();

		Given("a running example", () -> {
			camel.completeAsync(() -> {
				App.main.bind("app.dataSource", mysql.dataSource());

				App.main.addInitialProperty("kafka.bootstrapServers", kafka.getBootstrapServers());

				final MainConfigurationProperties configuration = App.main.configure();

				configuration.addRoutesBuilder(new EndpointRouteBuilder() {
					@Override
					public void configure() throws Exception {
						from(kafka("source.public.customers").brokers("{{kafka.bootstrapServers}}").clientId("approval-test"))
							.process(exchange -> {
								final Message message = exchange.getMessage();
								final String payload = message.getBody(String.class);
								payloads.add(payload);
							})
							.routeId("approval-tests");
					}
				});

				try {
					startCamel()
						.thenRun(() -> debezium.startSourceConnector(postgresql))
						.get();
				} catch (InterruptedException | ExecutionException e) {
					throw new ExceptionInInitializerError(e);
				}

				return App.main;
			});

			camel.get();
		});

		When("A row is inserted in the source database", postgresql::store);

		Then("a row is present in the destination database", (final Customer customer) -> {
			await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
				final Optional<Customer> loaded = mysql.load(customer.id);
				assertThat(loaded).contains(customer);
			});
		});

		After(EndToEnd.approvalTest(payloads));
	}

	static HookBody approvalTest(final List<String> payloads) {
		return scenario -> {
			final Loader<ApprovalNamer> initial = Approvals.namerCreater;

			Approvals.namerCreater = () -> {
				return new NamerWrapper(scenario::getName, () -> "test-harness/src/main/resources/features");
			};

			try (NamedEnvironment env = NamerFactory.withParameters(scenario.getName())) {
				for (final String payload : payloads) {
					Approvals.verifyJson(payload, new Options().withScrubber(replaceTimestamps()));
				}
			} finally {
				Approvals.namerCreater = initial;
			}
		};
	}

	static CompletableFuture<BaseMainSupport> startCamel() {
		final CompletableFuture<BaseMainSupport> futureCamel = new CompletableFuture<>();

		App.main.addMainListener(new MainListenerSupport() {
			@Override
			public void afterStart(final BaseMainSupport main) {
				futureCamel.complete(main);
				LifecycleSupport.registerFinisher(App.main::stop);
			}
		});

		CompletableFuture.runAsync(App::main)
			.handle((v, t) -> futureCamel.completeExceptionally(t));

		return futureCamel;
	}

	private static Scrubber replaceTimestamps() {
		return Scrubbers.scrubAll(
			new RegExScrubber("\"ts_ms\": [0-9]{13}", "\"ts_ms\": 872835240000"),
			new RegExScrubber("\"sequence\": .*,", "\"sequence\": \"[]\","),
			new RegExScrubber("\"txId\": [0-9]+", "\"txId\": 1"),
			new RegExScrubber("\"lsn\": [0-9]+", "\"lsn\": 1"));
	}
}
