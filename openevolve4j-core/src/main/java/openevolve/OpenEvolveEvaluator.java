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
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import openevolve.util.BashExecutor;
import openevolve.util.SolutionUtil;

public class OpenEvolveEvaluator implements Function<EvolveSolution, Map<String, Object>> {

	private static final Logger log = org.slf4j.LoggerFactory.getLogger(OpenEvolveEvaluator.class);

	private final Path runner;
	private final Duration evalTimeout;
	private final Collection<String> metrics;
	private final ObjectMapper mapper;
	private final Set<Path> links;
	private final Path basePath;

	public OpenEvolveEvaluator(Path runner, Path basePath, Set<Path> links, Collection<String> metrics, Duration timeoutConfig,
			ObjectMapper mapper) {
		Objects.requireNonNull(mapper);
		Objects.requireNonNull(basePath);
		this.links = links != null ? Set.copyOf(links) : Set.of();
		this.runner = runner;
		this.evalTimeout = timeoutConfig;
		this.mapper = mapper;
		this.metrics = metrics;
		this.basePath = basePath;
	}

	// Add static trace helper that checks isTraceEnabled before logging
	private static void trace(String format, Object... args) {
		if (log.isTraceEnabled()) {
			log.trace(format, args);
		}
	}

	@Override
	public Map<String, Object> apply(EvolveSolution solution) {
		Objects.requireNonNull(solution);
		trace("Applying evaluator to solution: {}", solution);
		try {
			if (solution.files() == null || solution.files().isEmpty()) {
				throw new IllegalArgumentException("Solution files must not be null or empty");
			}
			var newBasePath = SolutionUtil.newPath();
			trace("Created new base path: {}", newBasePath);
			for (var link : links) {
				var target = basePath.resolve(link);
				var linkPath = newBasePath.resolve(link);
				Files.createDirectories(linkPath.getParent());
				Files.createSymbolicLink(linkPath, target);
				trace("Created symlink: {} -> {}", linkPath, target);
			}
			for (var sourceFile : solution.files().entrySet()) {
				var sourcePath = newBasePath.resolve(sourceFile.getKey());
				try {
					Files.createDirectories(sourcePath.getParent());
					Files.writeString(sourcePath, sourceFile.getValue(),
							StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
					trace("Wrote source file: {} ({} bytes)", sourcePath, sourceFile.getValue() == null ? 0 : sourceFile.getValue().length());
				} catch (Exception e) {
					throw new RuntimeException("Failed to write source file: " + sourceFile.getKey(),
							e);
				}
			}
			trace("Running evaluator script: {} in {}", runner, newBasePath);
			var execResult = BashExecutor.runScript(runner, List.of(), evalTimeout, newBasePath, Map.of(),
					StandardCharsets.UTF_8);
			var metricsMap = execResult.extractMetrics(mapper, metrics);
			trace("Evaluation completed, metrics: {}", metricsMap);
			return metricsMap;
		} catch (Throwable t) {
			var errorMessage = "Failed to evaluate bash script: " + runner;
			trace("Evaluation failed: {} - {}", errorMessage, t.getMessage());
			return Map.of(Constants.COMBINED_SCORE, 0.0, "error", errorMessage);
		}
	}
}
