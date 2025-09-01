package openevolve.util;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.FileSystemResource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Util {

	public static final <T> void save(T object, ObjectMapper mapper, Path filePath,
			boolean overwrite) {
		Objects.requireNonNull(object);
		Objects.requireNonNull(mapper);
		Objects.requireNonNull(filePath);
		var writer = mapper.writerWithDefaultPrettyPrinter();
		try (var outputStream = Files.newOutputStream(filePath,
				overwrite ? StandardOpenOption.CREATE : StandardOpenOption.APPEND)) {
			writer.writeValue(outputStream, object);
		} catch (IOException e) {
			throw new RuntimeException("Failed to save object to " + filePath, e);
		}
	}

	public static final <T> T load(ObjectMapper mapper, Path filePath, TypeReference<T> type) {
		if (!Files.exists(filePath)) {
			return null;
		}
		try (var inputStream = Files.newInputStream(filePath)) {
			return mapper.readValue(inputStream, type);
		} catch (IOException e) {
			return null;
		}
	}

	public static double getDoubleScore(String key, Map<String, Object> metrics) {
		if (metrics != null) {
			var value = metrics.getOrDefault(key, Double.NEGATIVE_INFINITY);
			if (value instanceof Number) {
				return ((Number) value).doubleValue();
			}
		}
		return Double.NEGATIVE_INFINITY;
	}

	public static double getAvgScore(Map<String, Object> metrics) {
		return metrics.values().stream().filter(v -> v instanceof Number)
				.mapToDouble(v -> ((Number) v).doubleValue()).average()
				.orElse(Double.NaN);
	}

	public static <T> T parseJSON(String json, ObjectMapper mapper, TypeReference<T> typeRef,
			Predicate<T> test) {
		var parser = new StreamingJsonParser(mapper, false);
		var found = new AtomicReference<T>();
		parser.consume(node -> {
			try {
				var value = mapper.readValue(node.toString(), typeRef);
				if (test.test(value)) {
					found.set(value);
					parser.shouldStop();
				}
			} catch (JsonProcessingException e) {
				// ignored
			}
		});
		parser.feedText(json);
		return found.get();
	}

	public static <T> T selfOrDefault(T self, T defaultValue) {
		Objects.requireNonNull(defaultValue);
		if (self == null || (self instanceof Collection col && col.isEmpty())
				|| (self instanceof Map map && map.isEmpty())) {
			return defaultValue;
		}
		return self;
	}

	public static Map<String, List<PromptTemplate>> templatesFromPath(Path promptsPath, Map<String, List<PromptTemplate>> defaults) {
		if (promptsPath == null || !Files.exists(promptsPath)) {
			return new HashMap<>(defaults);
		}
		var prompts = new HashMap<String, List<PromptTemplate>>();
		var defaultKeys = defaults.keySet().stream().collect(Collectors.toMap(Function.identity(), k -> Pattern.compile("(" + k + ")")));
		try {
			Files.walkFileTree(promptsPath, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
					defaultKeys.entrySet().stream().forEach(entry -> {
						if (entry.getValue().matcher(file.getFileName().toString()).find()) {
							var templates = prompts.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
							templates.add(new PromptTemplate(new FileSystemResource(file.toAbsolutePath())));
						}
					});
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			throw new RuntimeException("Failed to walk file tree at " + promptsPath, e);
		}
		var result = new HashMap<>(defaults);
		for (var prompt : prompts.entrySet()) {
			var fileName = prompt.getKey();
			var templates = prompt.getValue();
			result.put(fileName, templates);
		}
		return result;
	}
}
