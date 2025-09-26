package openevolve;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import openevolve.agents.DeepResearchAgent;
import openevolve.util.PathKeyDeserializer;

public final class Constants {

	private Constants() {}

	public static final String SEED = "seed";

	public static final String COMPLEXITY = "complexity";
	public static final String DIVERSITY = "diversity";
	public static final String SCORE = "score";
	public static final String COMBINED_SCORE = "combined_score";

	// Prompt names
	public static final String SYSTEM_DEFAULT = "system_default";
	public static final String USER_DIFF = "user_diff";
	public static final String TASK = "task";
	public static final String SOLUTION = "solution";
	public static final String SOLUTION_FILE = "solution_file";
	public static final String USER_FULL_REWRITE = "user_full_rewrite";
	public static final String DEEPRESEARCH_PROMPT = "deepresearch_prompt";

	public static final TypeReference<Map<String, Object>> MAP_TYPE_REF =
			new TypeReference<Map<String, Object>>() {};

	public static final String SOURCE_START = "# === ";
	public static final String SOURCE_END = " ===";

	public static final Predicate<? super Map<?, ?>> EMPTY_CHECK = m -> m == null || m.isEmpty();

	public static final Map<String, PromptTemplate> DEFAULT_PROMPTS = List
			.of(SYSTEM_DEFAULT, USER_DIFF, USER_FULL_REWRITE, TASK, DEEPRESEARCH_PROMPT, SOLUTION,
					SOLUTION_FILE, DeepResearchAgent.SYSTEM_DEEPRESEARCH_REPORT,
					DeepResearchAgent.USER_DEEPRESEARCH_REPORT,
					DeepResearchAgent.SYSTEM_DEEPRESEARCH_ENSURE_CHECK,
					DeepResearchAgent.USER_DEEPRESEARCH_ENSURE_CHECK,
					DeepResearchAgent.SYSTEM_DEEPRESEARCH_FIND_SEGMENTS,
					DeepResearchAgent.USER_DEEPRESEARCH_FIND_SEGMENTS,
					DeepResearchAgent.SYSTEM_DEEPRESEARCH_SEARCH_PHRASES,
					DeepResearchAgent.USER_DEEPRESEARCH_SEARCH_PHRASES,
					DeepResearchAgent.SYSTEM_DEEPRESEARCH_TOPICS,
					DeepResearchAgent.USER_DEEPRESEARCH_TOPICS,
					DeepResearchAgent.SYSTEM_DEEPRESEARCH_DECOMPOSITION,
					DeepResearchAgent.USER_DEEPRESEARCH_DECOMPOSITION,
					DeepResearchAgent.SYSTEM_DEEPRESEARCH_VALIDATE,
					DeepResearchAgent.USER_DEEPRESEARCH_VALIDATE)
			.stream()
			.collect(Collectors.toMap(Function.identity(), name -> new PromptTemplate(
					new ClassPathResource("/openevolve/prompts/" + name + ".md"))));

	public static final ObjectMapper OBJECT_MAPPER =
			JsonMapper.builder().findAndAddModules().addModule(new JavaTimeModule())
					.addModule(new CustomModule()).enable(JsonParser.Feature.ALLOW_COMMENTS)
					.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
					.enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES)
					.enable(JsonReadFeature.ALLOW_MISSING_VALUES)
					.enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
					.enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
					.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
					.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false)
					.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
					.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build();

	public static final class CustomModule extends SimpleModule {
		public CustomModule() {
			super("CustomModule");
			addKeyDeserializer(Path.class, new PathKeyDeserializer());
		}
	}

	public static final FileVisitor<Path> DIRECTORY_CLEANER = new SimpleFileVisitor<Path>() {
		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			Files.deleteIfExists(file);
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
			Files.deleteIfExists(dir);
			return FileVisitResult.CONTINUE;
		}
	};

	public static final BiFunction<Path, Path, FileVisitor<Path>> COPY_VISITOR =
			(source, target) -> new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
						throws IOException {
					Path targetDir = target.resolve(source.relativize(dir));
					if (!Files.exists(targetDir)) {
						Files.createDirectories(targetDir);
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
						throws IOException {
					Path targetFile = target.resolve(source.relativize(file));
					Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
					return FileVisitResult.CONTINUE;
				}
			};
}
