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
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

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
	public static final String USER_FULL_REWRITE = "user_full_rewrite";

	public static final TypeReference<Map<String, Object>> MAP_TYPE_REF =
			new TypeReference<Map<String, Object>>() {};
	
	public static final String SOURCE_START = "# === ";
	public static final String SOURCE_END = " ===";

	public static final Predicate<? super Map<?, ?>> EMPTY_CHECK = m -> m == null || m.isEmpty();

	public static final Map<String, List<PromptTemplate>> DEFAULT_PROMPTS = List
			.of(SYSTEM_DEFAULT, USER_DIFF, USER_FULL_REWRITE, TASK, SOLUTION)
			.stream().collect(Collectors.toMap(Function.identity(), name -> List.of(new PromptTemplate(
					new ClassPathResource("/openevolve/prompts/" + name + ".md")))));

	public static final ObjectMapper YAML_MAPPER =
			new YAMLMapper().enable(SerializationFeature.INDENT_OUTPUT).findAndRegisterModules()
					.registerModule(new JavaTimeModule()).registerModule(pathDeserializerModule())
					.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	public static final ObjectMapper OBJECT_MAPPER =
			JsonMapper.builder().findAndAddModules().enable(JsonParser.Feature.ALLOW_COMMENTS)
					.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
					.enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES)
					.enable(JsonReadFeature.ALLOW_MISSING_VALUES)
					.enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
					.enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
					.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build();

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

	private static SimpleModule pathDeserializerModule() {
		var module = new SimpleModule();
		module.addDeserializer(openevolve.OpenEvolveConfig.Solution.class, new ConfigDeserializer());
		return module;
	}

	private static class ConfigDeserializer extends JsonDeserializer<openevolve.OpenEvolveConfig.Solution> {

		@Override
		public openevolve.OpenEvolveConfig.Solution deserialize(JsonParser p, DeserializationContext ctxt)
				throws IOException, JacksonException {
			var mapper = (ObjectMapper) p.getCodec();
			JsonNode node = mapper.readTree(p);
			ObjectNode mutableNode = node.deepCopy();
			var filePath = node.has("workspace") ? node.get("workspace").asText() : null;
			filePath = filePath != null ? filePath : (String) ctxt.getAttribute("filePath");
			mutableNode.put("workspace", filePath);
			var simpleMapper = new ObjectMapper();
			simpleMapper.findAndRegisterModules();
			simpleMapper.registerModule(new JavaTimeModule());
			return simpleMapper.treeToValue(mutableNode, openevolve.OpenEvolveConfig.Solution.class);
		}
	}
}
