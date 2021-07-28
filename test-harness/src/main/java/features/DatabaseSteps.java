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

import io.cucumber.java8.En;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import configuration.EndToEndTests;
import data.Customer;
import database.DestinationDatabase;
import database.SourceDatabase;

public final class DatabaseSteps {

	public static void registerWith(final En en) {
		en.When("A row is inserted in the source database", (final Customer customer) -> {
			final SourceDatabase sourceDatabase = EndToEndTests.sourceDatabase();

			sourceDatabase.store(customer);

			en.After(sourceDatabase::stop);
		});

		en.Then("a row is present in the destination database", (final Customer customer) -> {
			final DestinationDatabase destinationDatabase = EndToEndTests.destinationDatabase();

			await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
				final Optional<Customer> loaded = destinationDatabase.load(customer.id);
				assertThat(loaded).contains(customer);
			});

			en.After(destinationDatabase::stop);
		});

	}
}
