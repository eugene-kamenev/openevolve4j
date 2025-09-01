package openevolve;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import com.fasterxml.jackson.databind.ObjectMapper;
import openevolve.util.BashExecutor;

public class OpenEvolveEvaluator implements Function<EvolveSolution, Map<String, Object>> {

	private final Path runner;
	private final Duration evalTimeout;
	private final Collection<String> metrics;
	private final ObjectMapper mapper;

	public OpenEvolveEvaluator(Path runner, Collection<String> metrics, Duration timeoutConfig,
			ObjectMapper mapper) {
		Objects.requireNonNull(mapper);
		this.runner = runner;
		this.evalTimeout = timeoutConfig;
		this.mapper = mapper;
		this.metrics = metrics;
	}

	@Override
	public Map<String, Object> apply(EvolveSolution solution) {
		Objects.requireNonNull(solution);
		try {
			if (solution.content() == null || solution.content().isEmpty()) {
				throw new IllegalArgumentException("Solution content must not be null or empty");
			}
			Files.walkFileTree(solution.parentPath(),
					Constants.COPY_VISITOR.apply(solution.parentPath(), solution.path()));
			var code = Code.fromContent(solution.content(), solution.path());
			for (SourceFile sourceFile : code.files()) {
				var sourcePath = sourceFile.path();
				try {
					Files.createDirectories(sourcePath.getParent());
					Files.writeString(sourcePath, sourceFile.sourceCode(),
							StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
				} catch (Exception e) {
					throw new RuntimeException("Failed to write source file: " + sourceFile.path(),
							e);
				}
			}
			return BashExecutor.runScript(runner, List.of(), evalTimeout, solution.path(), Map.of(),
					StandardCharsets.UTF_8).extractMetrics(mapper, metrics);
		} catch (Throwable t) {
			var errorMessage = "Failed to evaluate bash script: " + runner;
			return Map.of(Constants.COMBINED_SCORE, 0.0, "error", errorMessage);
		}
	}
}
