package openevolve;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.cfg.ContextAttributes;
import openevolve.mapelites.ParetoComparator;
import openevolve.util.Util;

public record OpenEvolveConfig(
		Solution solution, Selection selection, Migration migration,
		Repository repository, MAPElites mapelites, LLM llm, Map<String, Boolean> metrics, Path promptPath,
		@JsonIgnore Comparator<openevolve.mapelites.Population.Solution<EvolveSolution>> comparator,
		@JsonIgnore Predicate<openevolve.mapelites.Population.Solution<EvolveSolution>> stopCondition,
		@JsonIgnore Map<String, List<PromptTemplate>> prompts) {

	public OpenEvolveConfig {
		if (metrics == null || metrics.isEmpty()) {
			throw new IllegalArgumentException("Metrics must not be null or empty");
		}
		if (solution == null) {
			throw new IllegalArgumentException("Solution config must not be null");
		}
		if (selection == null) {
			throw new IllegalArgumentException("Selection config must not be null");
		}
		if (migration == null) {
			throw new IllegalArgumentException("Migration config must not be null");
		}
		if (repository == null) {
			throw new IllegalArgumentException("Repository config must not be null");
		}
		if (mapelites == null) {
			throw new IllegalArgumentException("MAP-Elites config must not be null");
		}
		comparator = comparator == null ? defaultComparator(metrics) : comparator;
		stopCondition = stopCondition == null ? _ -> false : stopCondition;
		promptPath = promptPath != null ? solution.workspace().resolve(promptPath) : null;
		prompts = promptPath != null
				? Util.templatesFromPath(promptPath,
						prompts != null ? prompts : Constants.DEFAULT_PROMPTS)
				: Constants.DEFAULT_PROMPTS;
	}

	public static OpenEvolveConfig fromFile(Path configFilePath) {
		try (var configStream = Files.newInputStream(configFilePath)) {
			var attrs = ContextAttributes.getEmpty().withSharedAttribute("filePath",
					configFilePath.getParent().toString());
			return Constants.YAML_MAPPER.readerFor(OpenEvolveConfig.class).with(attrs)
					.readValue(configStream);
		} catch (IOException e) {
			throw new RuntimeException("Failed to load config from " + configFilePath, e);
		}
	}

	public record Solution(Path workspace, Path path, Path runner, Duration evalTimeout,
			Boolean fullRewrite, String pattern, @JsonIgnore Pattern filePattern) {
		public Solution {
			Objects.requireNonNull(workspace);
			path = workspace.resolve(path != null ? path : Path.of("solution"));
			runner = workspace.resolve(runner != null ? runner : Path.of("runner"));
			evalTimeout = evalTimeout == null ? Duration.ofMinutes(1) : evalTimeout;
			pattern = pattern == null || pattern.isEmpty() ? ".*\\.py$" : pattern;
			filePattern = filePattern == null ? Pattern.compile(pattern) : filePattern;
			fullRewrite = fullRewrite == null ? true : fullRewrite;
		}
	}

	public record Selection(Long seed, Double explorationRatio, Double exploitationRatio,
			Double eliteSelectionRatio, Integer numInspirations, Integer numberDiverse,
			Integer numberTop, @JsonIgnore Random random) {
		public Selection {
			seed = seed == null ? 42L : seed;
			random = random == null ? new Random(seed) : random;
			exploitationRatio = exploitationRatio == null ? 0.1 : exploitationRatio;
			explorationRatio = explorationRatio == null ? 0.1 : explorationRatio;
			eliteSelectionRatio = eliteSelectionRatio == null ? 0.1 : eliteSelectionRatio;
			numInspirations = numInspirations == null ? 5 : numInspirations;
			numberDiverse = numberDiverse == null ? 5 : numberDiverse;
			numberTop = numberTop == null ? 5 : numberTop;
			if (exploitationRatio < 0 || explorationRatio < 0 || eliteSelectionRatio < 0
					|| numInspirations < 0 || numberDiverse < 0 || numberTop < 0) {
				throw new IllegalArgumentException("All selection parameters must be non-negative");
			}
		}
	}

	public record Migration(Double rate, Integer interval) {
		public Migration {
			rate = rate == null ? 0.1 : rate;
			interval = interval == null ? 10 : interval;
			if (rate < 0) {
				throw new IllegalArgumentException("Migration rate must be non-negative");
			}
			if (interval < 0) {
				throw new IllegalArgumentException("Migration interval must be non-negative");
			}
		}
	}

	public record Repository(Integer checkpointInterval, Integer populationSize,
			Integer archiveSize, Integer islands) {

		public Repository {
			checkpointInterval = checkpointInterval == null ? 10 : checkpointInterval;
			populationSize = populationSize == null ? 50 : populationSize;
			archiveSize = archiveSize == null ? 10 : archiveSize;
			islands = islands == null ? 2 : islands;
			if (checkpointInterval < 0) {
				throw new IllegalArgumentException("Checkpoint interval must be non-negative");
			}
			if (populationSize < 0) {
				throw new IllegalArgumentException("Population size must be non-negative");
			}
			if (archiveSize < 0) {
				throw new IllegalArgumentException("Archive size must be non-negative");
			}
			if (islands < 0) {
				throw new IllegalArgumentException("Islands must be non-negative");
			}
		}
	}

	public record MAPElites(Integer numIterations, Integer bins, List<String> dimensions) {
		public MAPElites {
			numIterations = numIterations == null ? 100 : numIterations;
			bins = bins == null ? 10 : bins;
			if (bins <= 0) {
				throw new IllegalArgumentException("Bins must be positive");
			}
			if (numIterations <= 0) {
				throw new IllegalArgumentException("Number of iterations must be positive");
			}
			dimensions = dimensions != null && !dimensions.isEmpty() ? dimensions
					: List.of(Constants.SCORE, Constants.COMPLEXITY, Constants.DIVERSITY);
		}
	}

	public record LLM(List<OpenAiChatOptions> models) {
		public LLM {
			Objects.requireNonNull(models);
			if (models.isEmpty()) {
				throw new IllegalArgumentException("Models must not be empty");
			}
		}
	}

	private static Comparator<openevolve.mapelites.Population.Solution<EvolveSolution>> defaultComparator(
			Map<String, Boolean> metrics) {
		var names = new ArrayList<String>();
		var maximize = new boolean[metrics.size()];
		for (var entry : metrics.entrySet()) {
			names.add(entry.getKey());
			maximize[names.size() - 1] = entry.getValue();
		}
		return new ParetoComparator<>(maximize, new OpenEvolveMetricExtractor(names, maximize));
	}

}
