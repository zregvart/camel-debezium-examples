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

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cucumber.java8.En;
import io.cucumber.java8.StepDefinitionBody;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import data.Customer;
import database.MySQLDestinationDatabase;
import database.PostgreSQLSourceDatabase;

public final class CustomerSteps {
	private CustomerSteps() {
	}

	public static void registerWith(final En en, final AtomicBoolean expectingPayload, final PostgreSQLSourceDatabase postgresql,
		final MySQLDestinationDatabase mysql) {

		en.When("a row is inserted in the source database", (final Customer customer) -> {
			expectingPayload.set(true);
			postgresql.create(customer);
		});

		final StepDefinitionBody.A1<Customer> assertRowPresent = (final Customer customer) -> {
			await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
				final Optional<Customer> loaded = mysql.load(customer.id);
				assertThat(loaded).contains(customer);
			});
		};

		en.Then("a row is present in the destination database", assertRowPresent);

		en.When("a row is updated in the source database", (final Customer customer) -> {
			expectingPayload.set(true);
			postgresql.update(customer);
		});

		en.Then("an existing row is updated in the destination database", assertRowPresent);

		en.When("a row with the id of {int} deleted from the source database", (final Integer id) -> {
			expectingPayload.set(true);
			postgresql.delete(id);
		});

		en.Then("a row with the id of {int} doesn't exist in the destination database", (final Integer id) -> {
			await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
				final Optional<Customer> loaded = mysql.load(id);
				assertThat(loaded).isEmpty();
			});
		});

	}
}
