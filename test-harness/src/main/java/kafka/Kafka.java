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

import static configuration.EndToEndTests.newCompletableFuture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import configuration.EndToEndTests;
import configuration.LifecycleSupport;

public final class Kafka {

	static final CompletableFuture<KafkaContainer> container = newCompletableFuture();

	public Kafka() {
		container.completeAsync(() -> {
			@SuppressWarnings("resource")
			final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.2.0")).withNetwork(EndToEndTests.testNetwork);

			kafka.start();
			LifecycleSupport.registerFinisher(kafka::stop);

			return kafka;
		});
	}

	@SuppressWarnings("static-method")
	public KafkaContainer container() {
		try {
			return container.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	@SuppressWarnings("resource")
	public String getBootstrapServers() {
		return container().getBootstrapServers();
	}

}
