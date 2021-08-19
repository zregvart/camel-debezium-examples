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

import java.util.concurrent.atomic.AtomicBoolean;

import io.cucumber.java8.En;

import database.PostgreSQLSourceDatabase;

public final class DebeziumSteps {
	private DebeziumSteps() {
	}

	public static void registerWith(final En en, final AtomicBoolean expectingPayload, final PostgreSQLSourceDatabase postgresql) {
		en.When("a snapshot is triggered", () -> {
			expectingPayload.set(true);
			postgresql.triggerSnapshot();
		});
	}
}
