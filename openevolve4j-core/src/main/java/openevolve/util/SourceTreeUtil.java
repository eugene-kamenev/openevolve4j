package openevolve.util;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import openevolve.Constants;

public final class SourceTreeUtil {

	private SourceTreeUtil() {}

	public record PathStructure(Map<Path, String> target, Set<Path> linked, Path baseDir) {

		public static Map<Path, String> applyChanges(String code, Map<Path, String> base) {
			String[] codeLines = code.split(System.lineSeparator());
			var files = new LinkedHashMap<Path, String>();
			files.putAll(base);
			var sourceBuilder = new StringBuilder();
			String currentFileName = null;
			Consumer<String> consumer = fileName -> {
				var filePath = Path.of(toRelativePath(fileName));
				// TODO: what if files does not have such key?
				// In that case we will create new file
				files.put(filePath, sourceBuilder.toString());
				sourceBuilder.setLength(0);
			};
			for (int i = 0; i < codeLines.length; i++) {
				var line = codeLines[i];
				if (line.startsWith(Constants.SOURCE_START)
						&& line.endsWith(Constants.SOURCE_END)) {
					if (currentFileName != null) {
						consumer.accept(currentFileName);
					}
					currentFileName = line.substring(6, line.length() - 4).trim();
				} else {
					sourceBuilder.append(line).append(System.lineSeparator());
				}
			}
			if (currentFileName != null) {
				consumer.accept(currentFileName);
			}
			return files;
		}
	}

	private static final String toRelativePath(String fileName) {
		if (fileName.startsWith(File.separator)) {
			return fileName.substring(File.separator.length());
		}
		return fileName;
	}

	public static final String toContent(Map<Path, String> files) {
		if (files == null || files.isEmpty()) {
			return null;
		}
		var builder = new StringBuilder();
		for (Map.Entry<Path, String> entry : files.entrySet()) {
			builder.append(Constants.SOURCE_START).append(File.separator)
					.append(entry.getKey()).append(Constants.SOURCE_END)
					.append(System.lineSeparator()).append(System.lineSeparator())
					.append(entry.getValue()).append(System.lineSeparator())
					.append(System.lineSeparator());
		}
		return builder.toString();
	}

	public static final PathStructure initPath(Pattern matchPattern, Pattern ignorePattern, Path directory) {
		try {
			var copiedFiles = new LinkedHashSet<Path>();
			var linkedItems = new LinkedHashSet<Path>();
			processDirectoryRecursively(directory, matchPattern, ignorePattern, copiedFiles, linkedItems);
			var target = new LinkedHashMap<Path, String>();
			copiedFiles.stream().forEach(p -> {
				try {
					target.put(p, Files.readString(directory.resolve(p)));
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
			return new PathStructure(target, linkedItems, directory);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to process directory: " + directory, e);
		}
	}

	public static final Path newPath() {
		try {
			return Files.createTempDirectory("openevolve4j");
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to create temporary directory", e);
		}
	}

	private static void processDirectoryRecursively(Path baseDir, Pattern matchPattern, Pattern ignorePattern,
			Set<Path> matchedFiles, Set<Path> symbolicLinks) throws IOException {
		processDirectoryRecursively(baseDir, baseDir, matchPattern, ignorePattern, matchedFiles, symbolicLinks);
	}

	private static void processDirectoryRecursively(Path currentDir, Path baseDir, Pattern matchPattern, Pattern ignorePattern,
			Set<Path> matchedFiles, Set<Path> symbolicLinks) throws IOException {

		// Check if current directory should be ignored
		Path relativePath = baseDir.relativize(currentDir);
		String relativePathStr = relativePath.toString().replace('\\', '/');
		String dirName = currentDir.getFileName().toString();

		if (ignorePattern != null && (ignorePattern.matcher(dirName).matches()
				|| ignorePattern.matcher(relativePathStr).matches())) {
			return; // Skip this directory entirely
		}

		// Check if directory contains any matching files (recursively)
		boolean hasMatches = hasMatchingFiles(currentDir, matchPattern, ignorePattern);

		if (!hasMatches) {
			// No matches in this directory tree - add relative path to symbolic links
			symbolicLinks.add(relativePath);
			return;
		}

		// Directory has matches - need to process contents individually
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentDir)) {
			for (Path entry : stream) {
				// Check if entry should be ignored
				String entryName = entry.getFileName().toString();
				Path entryRelativePath = baseDir.relativize(entry);
				String entryRelativePathStr = entryRelativePath.toString().replace('\\', '/');

				if (ignorePattern != null && (ignorePattern.matcher(entryName).matches()
						|| ignorePattern.matcher(entryRelativePathStr).matches())) {
					continue; // Skip this entry
				}

				if (Files.isDirectory(entry)) {
					// Recursively process subdirectory
					processDirectoryRecursively(entry, baseDir, matchPattern,
							ignorePattern, matchedFiles, symbolicLinks);
				} else if (Files.isRegularFile(entry)) {
					if (matchPattern.matcher(entryName).matches()
							|| matchPattern.matcher(entryRelativePathStr).matches()) {
						// Add relative path of matching file
						matchedFiles.add(entryRelativePath);
					} else {
						// Add relative path for symbolic link
						symbolicLinks.add(entryRelativePath);
					}
				}
			}
		}
	}

	private static boolean hasMatchingFiles(Path directory, Pattern matchPattern,
			Pattern ignorePattern) throws IOException {
		return hasMatchingFilesRecursive(directory, directory, matchPattern, ignorePattern);
	}

	private static boolean hasMatchingFilesRecursive(Path currentDir, Path originalBaseDir,
			Pattern matchPattern, Pattern ignorePattern) throws IOException {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentDir)) {
			for (Path entry : stream) {
				// Check if entry should be ignored
				String entryName = entry.getFileName().toString();
				Path entryRelativePath = originalBaseDir.relativize(entry);
				String entryRelativePathStr = entryRelativePath.toString().replace('\\', '/');

				if (ignorePattern != null && (ignorePattern.matcher(entryName).matches()
						|| ignorePattern.matcher(entryRelativePathStr).matches())) {
					continue; // Skip this entry
				}

				if (Files.isRegularFile(entry)) {
					if (matchPattern.matcher(entryName).matches()
							|| matchPattern.matcher(entryRelativePathStr).matches()) {
						return true;
					}
				} else if (Files.isDirectory(entry)) {
					if (hasMatchingFilesRecursive(entry, originalBaseDir, matchPattern,
							ignorePattern)) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
