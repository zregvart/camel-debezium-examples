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
package kafka;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.picocontainer.Disposable;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import configuration.EndToEndTests;

public class Kafka implements Disposable {

	private final CompletableFuture<KafkaContainer> container = new CompletableFuture<>();

	public Kafka() {
		container.completeAsync(() -> new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.2.0"))
			.withNetwork(EndToEndTests.testNetwork))
			.thenAccept(KafkaContainer::start);
	}

	public KafkaContainer container() {
		try {
			return container.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	@Override
	public void dispose() {
		container.thenAccept(KafkaContainer::stop);
	}
}
