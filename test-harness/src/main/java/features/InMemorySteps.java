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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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

	private static final Customer[] DATABASE_SNAPSHOT = {
		new Customer(4, "Hayden", "Ventura", "hayden.ventura@example.com")
	};

	private static final ObjectMapper json = new ObjectMapper();

	public static void registerWith(final CamelContext camel, final En en) {
		en.When("a row is inserted in the source database", (final Customer customer) -> {
			try (ProducerTemplate producer = camel.createProducerTemplate()) {
				final ObjectNode record = JsonNodeFactory.instance.objectNode();
				record.putObject("source").put("table", "customers");
				record.putPOJO("after", customer);
				record.put("op", "c");

				producer.sendBody("direct:receive", json.writer().writeValueAsBytes(record));
				DATABASE.put(customer.id, customer);
			}
		});

		en.When("a row is updated in the source database", (final Customer customer) -> {
			try (ProducerTemplate producer = camel.createProducerTemplate()) {
				final ObjectNode record = JsonNodeFactory.instance.objectNode();
				record.putObject("source").put("table", "customers");
				record.putPOJO("before", DATABASE.get(customer.id));
				record.putPOJO("after", customer);
				record.put("op", "u");

				producer.sendBodyAndHeader("direct:receive", json.writer().writeValueAsBytes(record), "kafka.KEY", String.format("{\"id\":%d}", customer.id));
			}
		});

		en.When("a row with the id of {int} deleted from the source database", (final Integer id) -> {
			final Customer customer = DATABASE.remove(id);

			try (ProducerTemplate producer = camel.createProducerTemplate()) {
				final ObjectNode record = JsonNodeFactory.instance.objectNode();
				record.putObject("source").put("table", "customers");
				record.putPOJO("before", customer);
				record.put("op", "d");

				producer.sendBodyAndHeader("direct:receive", json.writer().writeValueAsBytes(record), "kafka.KEY", String.format("{\"id\":%d}", customer.id));
			}
		});

		en.Then("a row is present in the destination database", (final DataTable dataTable) -> assertProcessedSql(camel, "INSERT INTO customers (\n"
			+ "  email,\n"
			+ "  id,\n"
			+ "  first_name,\n"
			+ "  last_name\n"
			+ ") VALUES (\n"
			+ "  :?email,\n"
			+ "  :?id,\n"
			+ "  :?first_name,\n"
			+ "  :?last_name\n"
			+ ")",
			"INSERT INTO customers (\n"
				+ "  email,\n"
				+ "  id,\n"
				+ "  first_name,\n"
				+ "  last_name\n"
				+ ") VALUES (\n"
				+ "  :?email,\n"
				+ "  :?id,\n"
				+ "  :?first_name,\n"
				+ "  :?last_name\n"
				+ ") ON DUPLICATE KEY UPDATE\n"
				+ "  email=VALUES(email),\n"
				+ "  id=VALUES(id),\n"
				+ "  first_name=VALUES(first_name),\n"
				+ "  last_name=VALUES(last_name)"));

		en.Then("an existing row is updated in the destination database", (final DataTable dataTable) -> assertProcessedSql(camel, "UPDATE customers SET\n"
			+ "  email = :?email,\n"
			+ "  id = :?id,\n"
			+ "  first_name = :?first_name,\n"
			+ "  last_name = :?last_name\n"
			+ "WHERE\n"
			+ "  id = :?id"));

		en.Then("a row with the id of {int} doesn't exist in the destination database",
			(final Integer id) -> assertProcessedSql(camel, "DELETE FROM customers\n"
				+ "WHERE\n"
				+ "  id = :?id"));

		en.When("a snapshot is triggered", () -> {
			for (final Customer customer : DATABASE_SNAPSHOT) {
				try (ProducerTemplate producer = camel.createProducerTemplate()) {
					final ObjectNode record = JsonNodeFactory.instance.objectNode();
					record.putObject("source").put("table", "customers");
					record.putPOJO("after", customer);
					record.put("op", "r");

					producer.sendBodyAndHeader("direct:receive", json.writer().writeValueAsBytes(record), "kafka.KEY",
						String.format("{\"id\":%d}", customer.id));
				}
			}
		});

		en.Before(InMemorySteps::setupDatabase);
	}

	private static void assertProcessedSql(final CamelContext camel, final String statement, final String... alternatives) {
		@SuppressWarnings("resource")
		final MockEndpoint jdbc = camel.getEndpoint("mock:jdbc", MockEndpoint.class);

		assertThat(jdbc.getReceivedCounter()).isOne();

		final List<Consumer<String>> assertions = new ArrayList<>();
		assertions.add(s -> assertThat(s).isEqualTo(statement));

		for (final String alternative : alternatives) {
			assertions.add(s -> assertThat(s).isEqualTo(alternative));
		}

		@SuppressWarnings("unchecked")
		final Consumer<String>[] assertionsAry = assertions.toArray(Consumer[]::new);

		assertThat(jdbc.getReceivedExchanges().get(0).getIn().getBody(String.class)).satisfiesAnyOf(assertionsAry);
	}

	private static void setupDatabase() {
		DATABASE.clear();
		DATABASE.put(2, new Customer(2, "Jacob", "Yates", "jacob.yates@example.com"));
		DATABASE.put(3, new Customer(3, "Samara", "Quinton", "samara.quinton@example.com"));
	}
}
