package openevolve.puct;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.ai.chat.prompt.PromptTemplate;
import com.fasterxml.jackson.annotation.JsonIgnore;
import openevolve.Constants;
import openevolve.EvolveSolution;
import openevolve.OpenEvolveConfig.LLM;
import openevolve.OpenEvolveConfig.Solution;
import openevolve.api.AlgorithmConfig;
import openevolve.OpenEvolveMetricExtractor;
import openevolve.mapelites.ParetoComparator;
import openevolve.util.Util;

public record LLMPuctTreeConfig(
		Solution solution,
		Path promptPath,
		LLM llm,
		Set<Set<String>> llmGroups,
		Integer iterations,
		double explorationConstant,
		Map<String, Boolean> metrics,
		@JsonIgnore Comparator<openevolve.mapelites.Population.Solution<EvolveSolution>> comparator,
		@JsonIgnore Map<String, List<PromptTemplate>> prompts) implements AlgorithmConfig {

	public LLMPuctTreeConfig {
		Objects.requireNonNull(solution, "solution must not be null");
		Objects.requireNonNull(llm, "llm must not be null");
		Objects.requireNonNull(metrics, "metrics must not be null");
		if (comparator == null) {
			comparator = defaultComparator(metrics);
		}
		iterations = iterations != null && iterations > 0 ? iterations : 100;
		promptPath = promptPath != null ? solution.workspace().resolve(promptPath) : null;
		prompts = promptPath != null
				? Util.templatesFromPath(promptPath,
						prompts != null ? prompts : Constants.DEFAULT_PROMPTS)
				: Constants.DEFAULT_PROMPTS;
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
