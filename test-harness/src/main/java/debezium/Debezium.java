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

import static configuration.EndToEndTests.newCompletableFuture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.debezium.testing.testcontainers.ConnectorConfiguration;
import io.debezium.testing.testcontainers.DebeziumContainer;

import org.picocontainer.Disposable;
import org.testcontainers.containers.KafkaContainer;

import configuration.EndToEndTests;
import database.PostgreSQLSourceDatabase;
import kafka.Kafka;

public final class Debezium implements Disposable {

	final CompletableFuture<DebeziumContainer> container = newCompletableFuture();

	@SuppressWarnings("resource")
	public Debezium(final Kafka kafka) {
		container.completeAsync(() -> {
			final KafkaContainer kafkaContainer = kafka.container();

			final DebeziumContainer debezium = new DebeziumContainer("debezium/connect:1.6.0.Final")
				.withKafka(kafkaContainer)
				.dependsOn(kafkaContainer)
				.withNetwork(EndToEndTests.testNetwork);

			debezium.start();

			return debezium;
		});
	}

	@Override
	public void dispose() {
		container.thenAccept(DebeziumContainer::stop);
	}

	public void startSourceConnector(final PostgreSQLSourceDatabase postgresql) {
		final ConnectorConfiguration connector = ConnectorConfiguration.create()
			.with("connector.class", "io.debezium.connector.postgresql.PostgresConnector")
			.with("database.hostname", postgresql.hostname())
			.with("database.port", postgresql.port())
			.with("database.dbname", postgresql.name())
			.with("database.user", postgresql.username())
			.with("database.password", postgresql.password())
			.with("database.server.name", "source").with("plugin.name", "pgoutput");

		try {
			@SuppressWarnings("resource")
			final DebeziumContainer debezium = container.get();
			debezium.registerConnector("source", connector);
		} catch (InterruptedException | ExecutionException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

}
