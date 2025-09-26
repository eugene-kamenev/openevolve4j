package openevolve.util;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.core.io.FileSystemResource;
import org.springframework.retry.support.RetryTemplate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Util {

	public static ChatClient newChatClient(OpenAiChatOptions options, OpenAiApi openAiApi) {
		var chatModel = OpenAiChatModel.builder().openAiApi(openAiApi)
				.retryTemplate(RetryTemplate.builder().retryOn(Throwable.class)
						.exponentialBackoff(Duration.ofSeconds(10), 2, Duration.ofSeconds(30))
						.build())
				.defaultOptions(options).build();
		return ChatClient.builder(chatModel).build();
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

	public static Map<String, PromptTemplate> templatesFromPath(Path promptsPath, Map<String, PromptTemplate> defaults) {
		if (promptsPath == null || !Files.exists(promptsPath)) {
			return new HashMap<>(defaults);
		}
		var prompts = new HashMap<String, List<PromptTemplate>>();
		var defaultKeys = defaults.keySet().stream().collect(Collectors.toMap(Function.identity(), k -> Pattern.compile("(" + k + ")")));
		try {
			Files.walkFileTree(promptsPath, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					defaultKeys.entrySet().stream().forEach(entry -> {
						if (entry.getValue().matcher(file.getFileName().toString()).find()) {
							var templates = prompts.computeIfAbsent(entry.getKey(), _ -> new ArrayList<>());
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
			result.put(fileName, templates.get(0));
		}
		return result;
	}
}
