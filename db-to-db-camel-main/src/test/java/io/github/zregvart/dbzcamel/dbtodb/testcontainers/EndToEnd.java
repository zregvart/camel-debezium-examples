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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sql.DataSource;

import io.cucumber.java8.En;
import io.cucumber.java8.HookBody;
import io.debezium.testing.testcontainers.ConnectorConfiguration;
import io.debezium.testing.testcontainers.DebeziumContainer;
import io.github.zregvart.dbzcamel.dbtodb.App;

import org.apache.camel.Message;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.main.BaseMainSupport;
import org.apache.camel.main.MainConfigurationProperties;
import org.apache.camel.main.MainListenerSupport;
import org.approvaltests.Approvals;
import org.approvaltests.core.Options;
import org.approvaltests.namer.ApprovalNamer;
import org.approvaltests.namer.NamedEnvironment;
import org.approvaltests.namer.NamerFactory;
import org.approvaltests.namer.NamerWrapper;
import org.approvaltests.scrubbers.RegExScrubber;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import com.spun.util.persistence.Loader;

import configuration.EndToEndTests;
import database.SourceDatabase;
import features.DatabaseSteps;

public final class EndToEnd implements En {

	public EndToEnd() {
		final List<String> payloads = new CopyOnWriteArrayList<>();

		Given("a running example", () -> {
			@SuppressWarnings("resource")
			final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.2.0"))
				.withNetwork(EndToEndTests.testNetwork);
			kafka.start();
			After(kafka::stop);

			@SuppressWarnings("resource")
			final DebeziumContainer debezium = new DebeziumContainer("debezium/connect:1.6.0.Final")
				.withKafka(kafka)
				.dependsOn(kafka)
				.withNetwork(EndToEndTests.testNetwork);
			debezium.start();
			After(debezium::stop);

			final DataSource dataSource = EndToEndTests.destinationDatabase().dataSource();
			App.main.bind("app.dataSource", dataSource);

			App.main.addInitialProperty("kafka.bootstrapServers", kafka.getBootstrapServers());

			@SuppressWarnings("resource")
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

			startCamel()
				.thenApply(v -> debezium)
				.thenAccept(EndToEnd::startSourceConnector)
				.get(); // block until everything is running
		});

		After(App.main::stop);
		After(EndToEnd.approvalTest(payloads));

		DatabaseSteps.registerWith(this);
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
		final CompletableFuture<BaseMainSupport> camel = new CompletableFuture<>();

		App.main.addMainListener(new MainListenerSupport() {
			@Override
			public void afterStart(final BaseMainSupport main) {
				camel.complete(main);
			}
		});

		CompletableFuture.runAsync(App::main)
			.handle((v, t) -> camel.completeExceptionally(t));

		return camel;
	}

	static void startSourceConnector(final DebeziumContainer debezium) {
		final SourceDatabase database = EndToEndTests.sourceDatabase();
		final ConnectorConfiguration connector = ConnectorConfiguration.create()
			.with("connector.class", "io.debezium.connector.postgresql.PostgresConnector")
			.with("database.hostname", database.hostname())
			.with("database.port", database.port())
			.with("database.dbname", database.name())
			.with("database.user", database.username())
			.with("database.password", database.password())
			.with("database.server.name", "source").with("plugin.name", "pgoutput");

		debezium.registerConnector("source", connector);
	}

	private static RegExScrubber replaceTimestamps() {
		return new RegExScrubber("\"ts_ms\": [0-9]{13}", "\"ts_ms\": 872835240000");
	}
}
