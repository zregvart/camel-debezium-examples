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

import java.sql.DriverManager;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.postgresql.Driver;
import org.testcontainers.containers.JdbcDatabaseContainer;

import com.zaxxer.hikari.HikariDataSource;

import configuration.EndToEndTests;

abstract class BaseDatabase {

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

	private static DataSource createDataSource(final JdbcDatabaseContainer<?> database) {
		registerDrivers();

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

	/**
	 * Otherwise:
	 *
	 * <pre>
	 * Failures (1):
	 *   Cucumber:Data replication DB to DB:New row in source database is replicated to the destination
	 *     ClasspathResourceSource [classpathResourceName = features/db-to-db.feature, filePosition = FilePosition [line = 23, column = 3]]
	 *     => java.lang.RuntimeException: Failed to get driver instance for jdbcUrl=jdbc:postgresql://localhost:49538/source?loggerLevel=OFF
	 *        all//com.zaxxer.hikari.util.DriverDataSource.<init>(DriverDataSource.java:114)
	 *        all//com.zaxxer.hikari.pool.PoolBase.initializeDataSource(PoolBase.java:331)
	 *        all//com.zaxxer.hikari.pool.PoolBase.<init>(PoolBase.java:114)
	 *        all//com.zaxxer.hikari.pool.HikariPool.<init>(HikariPool.java:108)
	 *        all//com.zaxxer.hikari.HikariDataSource.getConnection(HikariDataSource.java:112)
	 *        [...]
	 *      Caused by: java.sql.SQLException: No suitable driver
	 *        java.sql/java.sql.DriverManager.getDriver(DriverManager.java:298)
	 *        all//com.zaxxer.hikari.util.DriverDataSource.<init>(DriverDataSource.java:106)
	 *        all//com.zaxxer.hikari.pool.PoolBase.initializeDataSource(PoolBase.java:331)
	 *        all//com.zaxxer.hikari.pool.PoolBase.<init>(PoolBase.java:114)
	 *        all//com.zaxxer.hikari.pool.HikariPool.<init>(HikariPool.java:108)
	 *        [...]
	 * </pre>
	 *
	 * Not sure why.
	 */
	private static void registerDrivers() {
		try {
			DriverManager.registerDriver(new Driver());
		} catch (final SQLException e) {
			throw new IllegalStateException("Unable to register JDBC driver with DriverManager", e);
		}
	}
}
