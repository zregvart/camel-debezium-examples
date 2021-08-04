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
package configuration;

import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;

import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.TestRunFinished;

public final class LifecycleSupport implements ConcurrentEventListener {

	private static final List<Finisher> finishers = new CopyOnWriteArrayList<>();

	@FunctionalInterface
	public interface Finisher {
		void finish();
	}

	@Override
	public void setEventPublisher(final EventPublisher publisher) {
		publisher.registerHandlerFor(TestRunFinished.class, this::handleTestRunFinished);
	}

	private void handleTestRunFinished(@SuppressWarnings("unused") final TestRunFinished finished) {
		for (final ListIterator<Finisher> i = finishers.listIterator(finishers.size()); i.hasPrevious();) {
			final Finisher finisher = i.previous();
			finisher.finish();
		}
	}

	public static void registerFinisher(final Finisher finisher) {
		finishers.add(finisher);
	}
}
