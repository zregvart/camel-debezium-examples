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
package approval;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Approval {

	private Approval() {
		// utility class
	}

	@SuppressWarnings("resource")
	public static <T> List<T> allPayloadsMapped(final Function<Path, T> mapper) {
		try {
			final URI features = Approval.class.getClassLoader().getResource("features").toURI();

			final FileSystem fileSystem = fileSystemFor(features);
			try {
				return Files.walk(fileSystem.getPath(pathFor(features)))
					.filter(path -> path.toString().endsWith(".approved.json"))
					.map(mapper)
					.collect(Collectors.toList());
			} finally {
				if (fileSystem != FileSystems.getDefault()) {
					fileSystem.close();
				}
			}
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		} catch (final URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public static String baseFileName(final Path path) {
		final Path fileNamePath = path.getFileName();
		if (fileNamePath == null) {
			return "";
		}

		final String fileName = fileNamePath.toString();

		return fileName.substring(0, fileName.length() - 14);
	}

	private static FileSystem fileSystemFor(final URI features) throws IOException {
		if ("file".equals(features.getScheme())) {
			return FileSystems.getFileSystem(features.resolve("/"));
		}

		return FileSystems.newFileSystem(features, Collections.emptyMap());
	}

	private static String pathFor(final URI features) {
		if ("file".equals(features.getScheme())) {
			return features.getPath();
		}

		return "features";
	}
}
