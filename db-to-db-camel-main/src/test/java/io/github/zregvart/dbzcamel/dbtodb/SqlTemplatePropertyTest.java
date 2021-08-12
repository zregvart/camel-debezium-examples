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

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.arbitraries.CharacterArbitrary;
import net.jqwik.api.arbitraries.ListArbitrary;
import net.jqwik.api.arbitraries.MapArbitrary;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserConstants;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

import freemarker.template.Configuration;
import freemarker.template.TemplateException;

public class SqlTemplatePropertyTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final Set<String> RESERVED_WORDS = Stream.concat(
		Stream.of("SEL"),
		Stream.of(CCJSqlParserConstants.tokenImage)
			.map(s -> s.substring(1, s.length() - 1)))
		.collect(Collectors.toUnmodifiableSet());

	final Configuration freemarker;

	public SqlTemplatePropertyTest() throws IOException {
		freemarker = new Configuration(Configuration.getVersion());
		final String insertTemplate = SqlTemplatePropertyTest.class.getResource("/sql.ftl").getFile();

		freemarker.setDirectoryForTemplateLoading(new File(insertTemplate).getParentFile());
	}

	@Property
	boolean generatedSqlStatementsAreParsable(@ForAll("kindsOfPayloads") final Map<String, Object> payload,
		@ForAll("kindsOfKeys") final Map<String, Object> keys) throws IOException, TemplateException {
		try (StringWriter out = new StringWriter()) {
			final Map<String, Object> data = new HashMap<>();
			data.put("body", payload);
			data.put("headers", Collections.singletonMap("kafka.KEY", JSON.writeValueAsString(keys)));

			freemarker.getTemplate("sql.ftl").process(data, out);

			final String sql = out.toString();
			try {
				// Camel uses `:?param` but the standard JDBC/SQL is `:param`
				// for named parameters
				CCJSqlParserUtil.parse(sql.replaceAll(":\\?", ":"));
			} catch (final JSQLParserException e) {
				throw new AssertionError("Unable to parse generated SQL: `" + sql + "`", e);
			}
		}

		return true;
	}

	static ListArbitrary<String> columnNames() {
		return identifier()
			.list().ofMinSize(1);
	}

	static Map<String, Object> createPayload(final String tableName, final List<String> columnNames, final Character operation) {
		final Map<String, Object> payload = new HashMap<>();
		final Map<String, Object> source = new HashMap<>();
		source.put("table", tableName);
		payload.put("source", source);
		payload.put("op", operation.toString());

		final Map<String, Object> after = new HashMap<>();
		payload.put("after", after);
		for (final String column : columnNames) {
			after.put(column, "");
		}

		return payload;
	}

	static Arbitrary<String> identifier() {
		return Arbitraries.strings()
			.alpha()
			.numeric()
			.ofMinLength(1)
			.ofMaxLength(7)
			.filter(s -> Character.isLetter(s.charAt(0)) && !RESERVED_WORDS.contains(s.toUpperCase(Locale.ENGLISH)));
	}

	@Provide
	static MapArbitrary<String, Integer> kindsOfKeys() {
		return Arbitraries.maps(identifier(), Arbitraries.integers())
			.ofMinSize(1);
	}

	@Provide
	static Arbitrary<Map<String, Object>> kindsOfPayloads() {
		return Combinators.combine(tableName(), columnNames(), operations())
			.as(SqlTemplatePropertyTest::createPayload);
	}

	static CharacterArbitrary operations() {
		return Arbitraries.chars().with('c', 'r', 'u', 'd');
	}

	static Arbitrary<String> tableName() {
		return identifier();
	}
}
