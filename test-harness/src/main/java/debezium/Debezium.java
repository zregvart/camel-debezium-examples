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
package debezium;

import static configuration.Async.newCompletableFuture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.debezium.testing.testcontainers.ConnectorConfiguration;
import io.debezium.testing.testcontainers.DebeziumContainer;

import org.testcontainers.containers.KafkaContainer;

import configuration.EndToEndTests;
import configuration.LifecycleSupport;
import database.PostgreSQLSourceDatabase;
import kafka.Kafka;

public final class Debezium {

	final static CompletableFuture<DebeziumContainer> CONTAINER = newCompletableFuture();

	private static final String CONNECTOR_NAME = "source";

	@SuppressWarnings("resource")
	public Debezium(final Kafka kafka) {
		CONTAINER.completeAsync(() -> {
			final KafkaContainer kafkaContainer = kafka.container();

			final DebeziumContainer debezium = new DebeziumContainer("debezium/connect:1.6.0.Final")
				.withKafka(kafkaContainer)
				.dependsOn(kafkaContainer)
				.withNetwork(EndToEndTests.TEST_NETWORK);

			debezium.start();
			LifecycleSupport.registerFinisher(debezium::stop);

			return debezium;
		});
	}

	@SuppressWarnings("static-method")
	public void startConnectorFor(final PostgreSQLSourceDatabase postgresql) {
		final ConnectorConfiguration connector = ConnectorConfiguration.create()
			.with("connector.class", "io.debezium.connector.postgresql.PostgresConnector")
			.with("database.hostname", postgresql.hostname())
			.with("database.port", postgresql.port())
			.with("database.dbname", postgresql.name())
			.with("database.user", postgresql.username())
			.with("database.password", postgresql.password())
			.with("database.server.name", CONNECTOR_NAME)
			.with("plugin.name", "pgoutput");

		try {
			@SuppressWarnings("resource")
			final DebeziumContainer debezium = CONTAINER.get();
			debezium.registerConnector(CONNECTOR_NAME, connector);
		} catch (InterruptedException | ExecutionException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

}
