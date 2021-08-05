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

import static configuration.Async.newCompletableFuture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import org.testcontainers.containers.MySQLContainer;

abstract class MySQLDatabase extends BaseDatabase {

	private static final CompletableFuture<State> STATE = newCompletableFuture();

	@SuppressWarnings("resource")
	MySQLDatabase(final String classifier) {
		super(classifier, new MySQLContainer<>("mysql:8"), MySQLContainer.MYSQL_PORT);
	}

	@Override
	final void complete(final Supplier<State> with) {
		STATE.completeAsync(with);
	}

	@Override
	final State state() {
		try {
			return STATE.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new ExceptionInInitializerError(e);
		}
	}
}
