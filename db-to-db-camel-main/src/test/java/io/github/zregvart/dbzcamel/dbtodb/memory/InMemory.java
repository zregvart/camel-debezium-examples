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
package io.github.zregvart.dbzcamel.dbtodb.memory;

import io.cucumber.java8.En;
import io.github.zregvart.dbzcamel.dbtodb.Route;

import org.apache.camel.CamelContext;

import static org.apache.camel.builder.AdviceWith.adviceWith;

import memory.Camel;

public final class InMemory implements En {

	public InMemory(final Camel camel) {
		@SuppressWarnings("resource")
		final CamelContext camelContext = camel.context();

		Given("a running example", () -> {
			camelContext.addRoutes(new Route());

			adviceWith(camelContext, "kafka-to-db", a -> {
				a.replaceFromWith("direct:receive");
				a.interceptSendToEndpoint("jdbc:*")
					.skipSendToOriginalEndpoint()
					.to("mock:jdbc");
			});

			camelContext.start();
		});
	}

}
