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
package features;

import java.util.HashMap;
import java.util.Map;

import io.cucumber.datatable.DataTable;
import io.cucumber.java8.En;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

import data.Customer;

public final class InMemorySteps {

	private static final Map<Integer, Customer> DATABASE = new HashMap<>();

	private static final ObjectMapper json = new ObjectMapper();

	public static void registerWith(final CamelContext camel, final En en) {
		en.When("A row is inserted in the source database", (final Customer customer) -> {
			try (ProducerTemplate producer = camel.createProducerTemplate()) {
				final ObjectNode record = JsonNodeFactory.instance.objectNode();
				record.putObject("source").put("table", "customers");
				record.putPOJO("after", customer);
				record.put("op", "c");

				producer.sendBody("direct:receive", json.writer().writeValueAsBytes(record));
				DATABASE.put(customer.id, customer);
			}
		});

		en.When("A row is updated in the source database", (final Customer customer) -> {
			try (ProducerTemplate producer = camel.createProducerTemplate()) {
				final ObjectNode record = JsonNodeFactory.instance.objectNode();
				record.putObject("source").put("table", "customers");
				record.putPOJO("before", DATABASE.get(customer.id));
				record.putPOJO("after", customer);
				record.put("op", "u");

				producer.sendBodyAndHeader("direct:receive", json.writer().writeValueAsBytes(record), "kafka.KEY", String.format("{\"id\":%d}", customer.id));
			}
		});

		en.When("A row with the id of {int} deleted from the source database", (final Integer id) -> {
			final Customer customer = DATABASE.remove(id);

			try (ProducerTemplate producer = camel.createProducerTemplate()) {
				final ObjectNode record = JsonNodeFactory.instance.objectNode();
				record.putObject("source").put("table", "customers");
				record.putPOJO("before", customer);
				record.put("op", "d");

				producer.sendBodyAndHeader("direct:receive", json.writer().writeValueAsBytes(record), "kafka.KEY", String.format("{\"id\":%d}", customer.id));
			}
		});

		en.Then("a row is present in the destination database", (final DataTable dataTable) -> {
			assertProcessedSql(camel, "INSERT INTO customers (\n"
				+ "  email,\n"
				+ "  id,\n"
				+ "  first_name,\n"
				+ "  last_name\n"
				+ ") VALUES (\n"
				+ "  :?email,\n"
				+ "  :?id,\n"
				+ "  :?first_name,\n"
				+ "  :?last_name\n"
				+ ")");
		});

		en.Then("an existing row is updated in the destination database", (final DataTable dataTable) -> {
			assertProcessedSql(camel, "UPDATE customers SET\n"
				+ "  email = :?email,\n"
				+ "  id = :?id,\n"
				+ "  first_name = :?first_name,\n"
				+ "  last_name = :?last_name\n"
				+ "WHERE\n"
				+ "  id = :?id");
		});

		en.Then("an row with the id of {int} doesn't exist in the destination database", (final Integer id) -> {
			assertProcessedSql(camel, "DELETE FROM customers\n"
				+ "WHERE\n"
				+ "  id = :?id");
		});

		en.Before(InMemorySteps::setupDatabase);
	}

	private static void assertProcessedSql(final CamelContext camel, final String statement) {
		@SuppressWarnings("resource")
		final MockEndpoint jdbc = camel.getEndpoint("mock:jdbc", MockEndpoint.class);

		assertThat(jdbc.getReceivedCounter()).isOne();
		assertThat(jdbc.getReceivedExchanges().get(0).getIn().getBody(String.class)).isEqualTo(statement);
	}

	private static void setupDatabase() {
		DATABASE.clear();
		DATABASE.put(2, new Customer(2, "John", "Doe", "john.doe@example.com"));
		DATABASE.put(3, new Customer(3, "John", "Doe", "john.doe@example.com"));
	}
}
