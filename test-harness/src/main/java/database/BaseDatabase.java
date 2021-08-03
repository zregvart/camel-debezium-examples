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

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.picocontainer.Disposable;
import org.testcontainers.containers.JdbcDatabaseContainer;

import com.zaxxer.hikari.HikariDataSource;

import configuration.EndToEndTests;
import data.Customer;

abstract class BaseDatabase implements Disposable {

	private final JdbcDatabaseContainer<?> database;

	private final DataSource dataSource;

	private final String hostname;

	private final String name;

	private final String password;

	private final int port;

	private final String username;

	@SuppressWarnings("resource")
	public BaseDatabase(final String classifier, final JdbcDatabaseContainer<?> database, final int databasePort) {
		database.withDatabaseName(classifier)
			.withNetwork(EndToEndTests.testNetwork)
			.withNetworkAliases(classifier)
			.start();

		hostname = classifier;

		port = databasePort;

		name = database.getDatabaseName();

		username = database.getUsername();

		password = database.getPassword();

		dataSource = createDataSource(database);

		migrate(classifier, dataSource);

		this.database = database;
	}

	public final DataSource dataSource() {
		return dataSource;
	}

	@Override
	public final void dispose() {
		database.stop();
	}

	public final String hostname() {
		return hostname;
	}

	public final String name() {
		return name;
	}

	public final String password() {
		return password;
	}

	public final int port() {
		return port;
	}

	public void stop() {
		database.stop();
	}

	public final String username() {
		return username;
	}

	Optional<Customer> load(final int id) {
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

	void store(final Customer customer) {
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

	private static DataSource createDataSource(final JdbcDatabaseContainer<?> database) {
		final HikariDataSource dataSource = new HikariDataSource();
		final String jdbcUrl = database.getJdbcUrl();
		dataSource.setJdbcUrl(jdbcUrl);
		dataSource.setUsername(database.getUsername());
		dataSource.setPassword(database.getPassword());

		return dataSource;
	}

	private static void migrate(final String classifier, final DataSource dataSource) {
		final Flyway flyway = Flyway.configure(Flyway.class.getClassLoader())
			.dataSource(dataSource)
			.locations("db/migration/" + classifier)
			.load();

		flyway.migrate();
	}
}
