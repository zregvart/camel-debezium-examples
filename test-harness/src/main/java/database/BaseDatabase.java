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
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;

import com.zaxxer.hikari.HikariDataSource;

import configuration.EndToEndTests;
import configuration.LifecycleSupport;
import data.Customer;

abstract class BaseDatabase {

	static {
		// Initialize Testcontainer's docker client early and on a single
		// thread, otherwise
		//
		// WARN o.t.d.DockerClientProviderStrategy - Can't instantiate a
		// strategy from
		// org.testcontainers.dockerclient.UnixSocketClientProviderStrategy
		// (ClassNotFoundException). This probably means that cached
		// configuration refers to a client provider class that is not available
		// in this version of Testcontainers. Other strategies will be tried
		// instead.
		//
		// ERROR o.t.d.DockerClientProviderStrategy - Could not find a valid
		// Docker environment. Please check configuration. Attempted
		// configurations were:
		//
		// ERROR o.t.d.DockerClientProviderStrategy - As no valid configuration
		// was found, execution cannot continue

		initializeTestcontainers();
	}

	static class State {

		private final DataSource dataSource;

		private final String hostname;

		private final String name;

		private final String password;

		private final int port;

		private final String username;

		public State(final DataSource dataSource, final String hostname, final String name, final String username,
			final String password, final int port) {
			this.dataSource = dataSource;
			this.hostname = hostname;
			this.name = name;
			this.username = username;
			this.password = password;
			this.port = port;
		}

	}

	@SuppressWarnings("resource")
	public BaseDatabase(final String classifier, final JdbcDatabaseContainer<?> container, final int databasePort) {
		complete(() -> {
			container.withDatabaseName(classifier)
				.withNetwork(EndToEndTests.TEST_NETWORK)
				.withNetworkAliases(classifier)
				.start();
			LifecycleSupport.registerFinisher(container::stop);

			final DataSource dataSource = createDataSource(container);

			migrate(classifier, dataSource);

			return new State(dataSource, classifier, container.getDatabaseName(), container.getUsername(), container.getPassword(), databasePort);
		});
	}

	public final DataSource dataSource() {
		return state().dataSource;
	}

	public final String hostname() {
		return state().hostname;
	}

	public final String name() {
		return state().name;
	}

	public final String password() {
		return state().password;
	}

	public final int port() {
		return state().port;
	}

	public final String username() {
		return state().username;
	}

	abstract void complete(Supplier<State> with);

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

	abstract State state();

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

	void update(final Customer customer) {
		try (Connection connection = dataSource().getConnection();
			PreparedStatement update = connection.prepareStatement("UPDATE customers SET first_name = ?, last_name = ?, email = ? WHERE id = ?")) {

			update.setString(1, customer.firstName);
			update.setString(2, customer.lastName);
			update.setString(3, customer.email);
			update.setInt(4, customer.id);

			update.executeUpdate();
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

		LifecycleSupport.registerFinisher(dataSource::close);

		return dataSource;
	}

	@SuppressWarnings("resource")
	private static void initializeTestcontainers() {
		DockerClientFactory.instance().client();
	}

	private static void migrate(final String classifier, final DataSource dataSource) {
		final Flyway flyway = Flyway.configure(Flyway.class.getClassLoader())
			.dataSource(dataSource)
			.locations("db/migration/" + classifier)
			.load();

		flyway.migrate();
	}

}
