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
package memory;

import java.util.Properties;

import javax.sql.DataSource;

import io.cucumber.java8.En;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.PropertiesComponent;

import static org.mockito.Mockito.mock;

public final class Camel implements En {
	private final CamelContext camel;

	public Camel() {
		camel = new DefaultCamelContext();
		camel.disableJMX();

		@SuppressWarnings("resource")
		final PropertiesComponent properties = camel.getPropertiesComponent();
		final Properties initial = new Properties();
		initial.put("kafka.bootstrapServers", "not used");
		properties.setInitialProperties(initial);

		camel.getRegistry().bind("app.dataSource", mock(DataSource.class));

		After(camel::stop);
	}

	public CamelContext camel() {
		return camel;
	}
}
