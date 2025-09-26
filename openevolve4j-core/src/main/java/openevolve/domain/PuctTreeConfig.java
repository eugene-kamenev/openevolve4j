package openevolve.domain;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import com.fasterxml.jackson.annotation.JsonIgnore;
import openevolve.Constants;
import openevolve.util.ParetoComparator;
import openevolve.util.Util;

public record PuctTreeConfig(
		Solution solution,
		Path promptPath,
		LLM llm,
		Integer iterations,
		double explorationConstant,
		Map<String, Boolean> metrics,
		@JsonIgnore Comparator<Map<String, Object>> comparator,
		@JsonIgnore Map<String, PromptTemplate> prompts) {

	public PuctTreeConfig {
		Objects.requireNonNull(solution, "solution must not be null");
		Objects.requireNonNull(llm, "llm must not be null");
		Objects.requireNonNull(metrics, "metrics must not be null");
		if (comparator == null) {
			comparator = defaultComparator(metrics);
		}
		iterations = iterations != null && iterations > 0 ? iterations : 1000;
		promptPath = promptPath != null ? solution.workspace().resolve(promptPath) : null;
		prompts = promptPath != null
				? Util.templatesFromPath(promptPath,
						prompts != null ? prompts : Constants.DEFAULT_PROMPTS)
				: Constants.DEFAULT_PROMPTS;
	}

	private static Comparator<Map<String, Object>> defaultComparator(
			Map<String, Boolean> metrics) {
		var names = new String[metrics.size()];
		var maximize = new boolean[metrics.size()];
		int i = 0;
		for (var entry : metrics.entrySet()) {
			names[i] = entry.getKey();
			maximize[i] = entry.getValue();
			i++;
		}
		return new ParetoComparator(maximize, names);
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

	public record LLM(List<OpenAiChatOptions> models) {
		public LLM {
			Objects.requireNonNull(models);
			if (models.isEmpty()) {
				throw new IllegalArgumentException("Models must not be empty");
			}
		}
	}
}
