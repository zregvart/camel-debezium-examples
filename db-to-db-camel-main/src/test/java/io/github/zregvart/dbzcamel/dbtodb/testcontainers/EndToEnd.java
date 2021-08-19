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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.cucumber.java8.En;
import io.github.zregvart.dbzcamel.dbtodb.App;

import org.apache.camel.CamelContext;
import org.apache.camel.main.BaseMainSupport;
import org.apache.camel.main.MainListenerSupport;

import configuration.Async;
import configuration.LifecycleSupport;
import database.MySQLDestinationDatabase;
import database.PostgreSQLSourceDatabase;
import debezium.Debezium;
import kafka.Kafka;
import kafka.Payloads;

public final class EndToEnd implements En {

	private static CompletableFuture<CamelContext> camel = newCompletableFuture();

	public EndToEnd(final PostgreSQLSourceDatabase postgresql, final MySQLDestinationDatabase mysql, final Debezium debezium, final Kafka kafka,
		final Payloads payloads) {
		Given("a running example", () -> {
			camel.completeAsync(() -> runExample(postgresql, mysql, kafka, debezium))
				.thenAccept(payloads::setupPayloadGathering)
				.get();
		});
	}

	static CamelContext runExample(final PostgreSQLSourceDatabase postgresql, final MySQLDestinationDatabase mysql, final Kafka kafka,
		final Debezium debezium) {
		App.main.bind("app.dataSource", mysql.dataSource());

		App.main.addInitialProperty("kafka.bootstrapServers", kafka.getBootstrapServers());

		App.main.addMainListener(new MainListenerSupport() {
			@Override
			public void afterStart(final BaseMainSupport main) {
				@SuppressWarnings("resource")
				final CamelContext camelContext = App.main.getCamelContext();
				camel.complete(camelContext);
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

}
