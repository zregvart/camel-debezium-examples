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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.testcontainers.containers.PostgreSQLContainer;

import data.Customer;

public final class PostgreSqlDatabase extends BaseDatabase implements SourceDatabase {

	@SuppressWarnings("resource")
	public PostgreSqlDatabase(final String classifier) {
		super(classifier, postgres(), PostgreSQLContainer.POSTGRESQL_PORT);
	}

	@Override
	public void store(final Customer customer) {
		try (Connection connection = dataSource().getConnection();
		    PreparedStatement insert = connection.prepareStatement("INSERT INTO customers (id, first_name, last_name, email) VALUES (?, ?, ?, ?)")) {

			insert.setInt(1, customer.id);
			insert.setString(2, customer.firstName);
			insert.setString(3, customer.lastName);
			insert.setString(4, customer.email);

			insert.executeUpdate();
		} catch (final SQLException e) {
			throw new AssertionError(e);
		}
	}

	private static PostgreSQLContainer<?> postgres() {
		final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13-alpine");
		final String[] commandParts = postgres.getCommandParts();
		final String[] newCommandParts = new String[commandParts.length + 2];
		System.arraycopy(commandParts, 0, newCommandParts, 0, commandParts.length);
		newCommandParts[newCommandParts.length - 2] = "-c";
		newCommandParts[newCommandParts.length - 1] = "wal_level=logical";
		postgres.setCommandParts(newCommandParts);

		return postgres;
	}

}
