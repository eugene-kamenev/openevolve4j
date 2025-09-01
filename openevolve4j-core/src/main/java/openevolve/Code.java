package openevolve;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public record Code(String code, List<SourceFile> files) {

	public static Code fromContent(String code, Path basePath) {
		String[] codeLines = code.split(System.lineSeparator());
		var files = new ArrayList<SourceFile>();
		var sourceBuilder = new StringBuilder();
		String currentFileName = null;
		for (int i = 0; i < codeLines.length; i++) {
			var line = codeLines[i];
			if (line.startsWith(Constants.SOURCE_START) && line.endsWith(Constants.SOURCE_END)) {
				if (currentFileName != null) {
					files.add(new SourceFile(sourceBuilder.toString(), basePath.resolve(toRelativePath(currentFileName))));
					sourceBuilder.setLength(0);
				}
				currentFileName = line.substring(6, line.length() - 4).trim();
			} else {
				sourceBuilder.append(line).append(System.lineSeparator());
			}
		}
		if (currentFileName != null) {
			files.add(new SourceFile(sourceBuilder.toString(), basePath.resolve(toRelativePath(currentFileName))));
		}
		return new Code(code, files);
	}

	public static Code fromPath(Path path, Pattern filePattern) {
		var files = new ArrayList<SourceFile>();
		try (var stream = Files.walk(path)) {
			stream.filter(Files::isRegularFile)
					.filter(file -> filePattern.matcher(file.getFileName().toString()).matches())
					.forEach(file -> {
						try {
							var content = Files.readString(file);
							files.add(new SourceFile(content, toRelativePath(path, file.toAbsolutePath())));
						} catch (IOException e) {
							throw new RuntimeException("Failed to read source file: " + file, e);
						}
					});
		} catch (IOException e) {
			throw new RuntimeException("Failed to read source files from path: " + path, e);
		}
		var builder = new StringBuilder();
		for (SourceFile file : files) {
			builder.append(Constants.SOURCE_START).append(File.separator)
					.append(file.path()).append(Constants.SOURCE_END).append(System.lineSeparator())
					.append(System.lineSeparator()).append(file.sourceCode())
					.append(System.lineSeparator()).append(System.lineSeparator());
		}
		return new Code(builder.toString(), files);
	}

	private static Path toRelativePath(Path parentPath, Path fullPath) {
		if (fullPath == null || parentPath == null) {
			return null;
		}
		return parentPath.relativize(fullPath);
	}

	private static String toRelativePath(String fileName) {
		if (fileName.startsWith(File.separator)) {
			return fileName.substring(File.separator.length());
		}
		return fileName;
	}
}
