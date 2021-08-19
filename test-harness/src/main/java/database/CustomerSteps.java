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
package database;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.cucumber.java8.En;
import io.cucumber.java8.StepDefinitionBody;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import data.Customer;
import kafka.Payloads;

public final class CustomerSteps implements En {

	public CustomerSteps(final PostgreSQLSourceDatabase postgresql, final MySQLDestinationDatabase mysql, final Payloads payloads) {
		When("a row is inserted in the source database", (final Customer customer) -> {
			payloads.expectPayload();
			postgresql.create(customer);
		});

		final StepDefinitionBody.A1<Customer> assertRowPresent = (final Customer customer) -> {
			await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
				final Optional<Customer> loaded = mysql.load(customer.id);
				assertThat(loaded).contains(customer);
			});
		};

		Then("a row is present in the destination database", assertRowPresent);

		When("a row is updated in the source database", (final Customer customer) -> {
			payloads.expectPayload();
			postgresql.update(customer);
		});

		Then("an existing row is updated in the destination database", assertRowPresent);

		When("a row with the id of {int} deleted from the source database", (final Integer id) -> {
			payloads.expectPayload();
			postgresql.delete(id);
		});

		Then("a row with the id of {int} doesn't exist in the destination database", (final Integer id) -> {
			await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
				final Optional<Customer> loaded = mysql.load(id);
				assertThat(loaded).isEmpty();
			});
		});
	}

}
