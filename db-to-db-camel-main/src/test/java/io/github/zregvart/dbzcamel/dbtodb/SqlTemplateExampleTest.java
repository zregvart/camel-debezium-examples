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
package io.github.zregvart.dbzcamel.dbtodb;

import static approval.Approval.allPayloadsMapped;
import static approval.Approval.baseFileName;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.approvaltests.Approvals;
import org.approvaltests.namer.NamedEnvironment;
import org.approvaltests.namer.NamerFactory;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import freemarker.template.TemplateNotFoundException;

public class SqlTemplateExampleTest {

	final Configuration freemarker;

	final ObjectMapper jackson = new ObjectMapper();

	SqlTemplateExampleTest() throws IOException {
		freemarker = new Configuration(Configuration.getVersion());
		final var insertTemplate = SqlTemplateExampleTest.class.getResource("/sql.ftl").getFile();

		freemarker.setDirectoryForTemplateLoading(new File(insertTemplate).getParentFile());
	}

	@ParameterizedTest(name = "{index}: {0}")
	@MethodSource("payloadsFromApprovalTests")
	void shouldGenerateExpectedStatements(final String baseFileName, final String payload) throws TemplateNotFoundException, TemplateException, IOException {
		final Map<?, ?> body = jackson.readValue(payload, Map.class);

		try (var out = new StringWriter()) {
			freemarker.getTemplate("sql.ftl").process(Collections.singletonMap("body", body), out);

			final var sql = out.toString();

			try (NamedEnvironment env = NamerFactory.withParameters(baseFileName)) {
				Approvals.verify(sql);
			}
		}
	}

	static List<Arguments> payloadsFromApprovalTests() {
		return allPayloadsMapped(path -> Arguments.of(Named.of(baseFileName(path), baseFileName(path)), readString(path)));
	}

	private static String readString(final Path path) {
		try {
			return Files.readString(path);
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
