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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.testcontainers.containers.MySQLContainer;

import data.Customer;

public final class MySQLDatabase extends BaseDatabase implements DestinationDatabase {

	@SuppressWarnings("resource")
	public MySQLDatabase(final String classifier) {
		super(classifier, new MySQLContainer<>("mysql:8"), MySQLContainer.MYSQL_PORT);
	}

	@Override
	public Optional<Customer> load(final int id) {
		try (Connection connection = dataSource().getConnection();
		    PreparedStatement select = connection.prepareStatement("SELECT id, first_name, last_name, email FROM customers WHERE id = ?")) {

			select.setInt(1, id);

			try (ResultSet row = select.executeQuery()) {
				if (!row.next()) {
					return Optional.empty();
				}

				return Optional.of(
				    new Customer(id,
				        row.getString("first_name"),
				        row.getString("last_name"),
				        row.getString("email")));
			}
		} catch (final SQLException e) {
			throw new AssertionError(e);
		}
	}

}
