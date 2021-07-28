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

import java.util.Properties;

import javax.sql.DataSource;

import io.cucumber.java8.En;
import io.github.zregvart.dbzcamel.dbtodb.Route;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.PropertiesComponent;

import static org.apache.camel.builder.AdviceWith.adviceWith;
import static org.mockito.Mockito.mock;

import features.InMemorySteps;

public final class InMemory implements En {

	final CamelContext camel;

	public InMemory() {
		camel = new DefaultCamelContext();
		camel.disableJMX();

		Given("a running example", () -> {
			@SuppressWarnings("resource")
			final PropertiesComponent properties = camel.getPropertiesComponent();
			final Properties initial = new Properties();
			initial.put("kafka.bootstrapServers", "not used");
			properties.setInitialProperties(initial);

			camel.getRegistry().bind("app.dataSource", mock(DataSource.class));

			camel.addRoutes(new Route());

			adviceWith(camel, "kafka-to-db", a -> {
				a.replaceFromWith("direct:receive");
				a.interceptSendToEndpoint("jdbc:*")
					.skipSendToOriginalEndpoint()
					.to("mock:jdbc");
			});

			camel.start();
		});

		After(camel::stop);

		InMemorySteps.registerWith(camel, this);
	}

}
