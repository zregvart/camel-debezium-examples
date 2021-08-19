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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cucumber.java8.En;
import io.cucumber.java8.HookBody;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.apache.camel.spi.RouteController;
import org.approvaltests.Approvals;
import org.approvaltests.core.Options;
import org.approvaltests.core.Scrubber;
import org.approvaltests.namer.ApprovalNamer;
import org.approvaltests.namer.NamedEnvironment;
import org.approvaltests.namer.NamerFactory;
import org.approvaltests.namer.NamerWrapper;
import org.approvaltests.scrubbers.RegExScrubber;
import org.approvaltests.scrubbers.Scrubbers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spun.util.persistence.Loader;

import static org.assertj.core.api.Assertions.assertThat;

public final class Payloads implements En {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	final AtomicBoolean expectingPayload = new AtomicBoolean();

	private final GatherPayloads gatherPayloads;

	private static final class GatherPayloads extends EndpointRouteBuilder {
		private final String bootstrapServers;

		private CamelContext camelContext;

		private final String id;

		private final BlockingQueue<Payload> payloadQueue = new ArrayBlockingQueue<>(5);

		private final List<Payload> payloads;

		private GatherPayloads(final List<Payload> payloads, final String boostrapServers) {
			this.payloads = payloads;
			bootstrapServers = boostrapServers;
			id = UUID.randomUUID().toString();
		}

		@Override
		public void configure() throws Exception {
			from(kafka("source.public.customers").brokers(bootstrapServers).clientId("approval-test-" + id))
				.routeId(id)
				.process(exchange -> {
					final Message message = exchange.getMessage();
					final String rawPayload = message.getBody(String.class);
					if (rawPayload == null) {
						return;
					}
					final Payload payload = new Payload(message.getHeaders(), MAPPER.readTree(rawPayload));
					payloads.add(payload);
					payloadQueue.add(payload);
				});

			camelContext = getContext();
		}

		void stop() {
			if (camelContext == null) {
				return;
			}

			try {
				@SuppressWarnings("resource")
				final RouteController routeController = camelContext.getRouteController();
				routeController.stopRoute(id);
				camelContext.removeRoute(id);
			} catch (final Exception e) {
				throw new IllegalStateException(e);
			}
		}
	}

	public Payloads(final Kafka kafka) {
		final List<Payload> payloads = new ArrayList<>();
		gatherPayloads = new GatherPayloads(payloads, kafka.getBootstrapServers());

		AfterStep(() -> {
			if (expectingPayload.compareAndSet(true, false)) {
				// wait for message to be delivered in order to proceed
				gatherPayloads.payloadQueue.take();
			}
		});

		After(gatherPayloads::stop);

		After(approvalTest(payloads));
	}

	public void expectPayload() {
		expectingPayload.set(true);
	}

	public void setupPayloadGathering(final CamelContext camel) {
		try {
			camel.addRoutes(gatherPayloads);
		} catch (final Exception e) {
			throw new IllegalStateException(e);
		}
	}

	static HookBody approvalTest(final List<Payload> payloads) {
		return scenario -> {
			assertThat(payloads).as("No payloads received, meaning no messages were received and processed from Kafka topic. "
				+ "Would expect at least one message to be received for the test to be valid.").isNotEmpty();

			final Loader<ApprovalNamer> initial = Approvals.namerCreater;

			Approvals.namerCreater = () -> {
				return new NamerWrapper(scenario::getName, () -> "test-harness/src/main/resources/features");
			};

			try (NamedEnvironment env = NamerFactory.withParameters(scenario.getName())) {
				Approvals.verifyJson(MAPPER.writeValueAsString(payloads), new Options().withScrubber(scrubber()));
			} finally {
				Approvals.namerCreater = initial;
				payloads.clear();
			}
		};
	}

	private static Scrubber scrubber() {
		return Scrubbers.scrubAll(
			new RegExScrubber("\"ts_ms\": [0-9]{13}", "\"ts_ms\": 872835240000"),
			new RegExScrubber("\"CamelMessageTimestamp\": [0-9]{13}", "\"CamelMessageTimestamp\": 872835240000"),
			new RegExScrubber("\"kafka.TIMESTAMP\": [0-9]{13}", "\"kafka.TIMESTAMP\": 872835240000"),
			new RegExScrubber("\"kafka.OFFSET\": [0-9]+", "\"kafka.OFFSET\": 0"),
			new RegExScrubber("\"sequence\": .*,", "\"sequence\": \"[]\","),
			new RegExScrubber("\"txId\": [0-9]+", "\"txId\": 1"),
			new RegExScrubber("\"lsn\": [0-9]+", "\"lsn\": 1"));
	}
}
