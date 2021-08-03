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

import org.postgresql.Driver;
import org.testcontainers.containers.PostgreSQLContainer;

abstract class PostgreSQLDatabase extends BaseDatabase {

	@SuppressWarnings("resource")
	PostgreSQLDatabase(final String classifier) {
		super(classifier, postgres(), PostgreSQLContainer.POSTGRESQL_PORT);
		registerDriver();
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
	private static void registerDriver() {
		try {
			DriverManager.registerDriver(new Driver());
		} catch (final SQLException e) {
			throw new IllegalStateException("Unable to register JDBC driver with DriverManager", e);
		}
	}
}
