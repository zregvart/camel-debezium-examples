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

import java.util.concurrent.CompletableFuture;

import javax.sql.DataSource;

import io.cucumber.java8.En;
import io.debezium.testing.testcontainers.ConnectorConfiguration;
import io.debezium.testing.testcontainers.DebeziumContainer;
import io.github.zregvart.dbzcamel.dbtodb.App;

import org.apache.camel.main.BaseMainSupport;
import org.apache.camel.main.MainListenerSupport;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import configuration.EndToEndTests;
import database.SourceDatabase;
import features.DatabaseSteps;

public final class EndToEnd implements En {

	public EndToEnd() {
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

			startCamel()
				.thenApply(v -> debezium)
				.thenAccept(EndToEnd::startSourceConnector)
				.get(); // block until everything is running

			After(App.main::stop);
		});

		DatabaseSteps.registerWith(this);
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
}
