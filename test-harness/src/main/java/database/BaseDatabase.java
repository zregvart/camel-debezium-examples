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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.picocontainer.Disposable;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;

import com.zaxxer.hikari.HikariDataSource;

import configuration.EndToEndTests;
import data.Customer;

abstract class BaseDatabase implements Disposable {

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

	private final CompletableFuture<State> state = new CompletableFuture<>();

	private static class State {

		private final JdbcDatabaseContainer<?> container;

		private final DataSource dataSource;

		private final String hostname;

		private final String name;

		private final String password;

		private final int port;

		private final String username;

		public State(final JdbcDatabaseContainer<?> container, final DataSource dataSource, final String hostname, final String name, final String username,
			final String password, final int port) {
			this.container = container;
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
		state.completeAsync(() -> {
			container.withDatabaseName(classifier)
				.withNetwork(EndToEndTests.testNetwork)
				.withNetworkAliases(classifier)
				.start();

			final DataSource dataSource = createDataSource(container);

			migrate(classifier, dataSource);

			return new State(container, dataSource, classifier, container.getDatabaseName(), container.getUsername(), container.getPassword(), databasePort);
		});
	}

	public final DataSource dataSource() {
		return state().dataSource;
	}

	@Override
	public final void dispose() {
		state.thenAccept(s -> s.container.stop());
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

	private State state() {
		try {
			return state.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new ExceptionInInitializerError(e);
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
