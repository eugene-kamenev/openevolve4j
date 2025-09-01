package openevolve;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.ai.chat.prompt.PromptTemplate;
import openevolve.util.CodeParsingUtils;

public abstract class BaseAgent {

	protected final Map<String, List<PromptTemplate>> templates;
	protected final LLMEnsemble llmEnsemble;
	protected final Random random;

	public BaseAgent(Map<String, List<PromptTemplate>> templates, LLMEnsemble llmEnsemble, Random random) {
		this.templates = templates;
		this.llmEnsemble = llmEnsemble;
		this.random = random;
	}

	public EvolveSolution newSolution(EvolveStep step, String llmResponse) {
		String changes = null;
		String newSolution = null;
		var solution = step.parent();
		if (solution.solution().fullRewrite()) {
			newSolution = CodeParsingUtils.parseFullRewrite(llmResponse, solution.solution().language()).orElse(null);
			changes = "Full rewrite";
		} else if (llmResponse != null && !llmResponse.isBlank()) {
			var diffs = CodeParsingUtils.extractDiffs(llmResponse);
			if (!diffs.isEmpty()) {
				newSolution = CodeParsingUtils.applyDiff(solution.solution().content(), llmResponse);
				changes = CodeParsingUtils.formatDiffSummary(diffs);
			}
		}
		if (newSolution != null) {
			var randString = RandomStringUtils.secure().next(8, true, true);
			var parentPath = solution.solution().path().getParent();
			var evolvedSolution = new EvolveSolution(
				solution.id(),
				solution.solution().path(),
				parentPath.resolve(randString),
				newSolution,
				solution.solution().language(),
				changes,
				solution.fitness(),
				solution.solution().fullRewrite()
			);
			return evolvedSolution;
		}
		return null;
	}

	public PromptTemplate getTemplate(String key) {
		var templateList = templates.get(key);
		if (templateList != null && !templateList.isEmpty()) {
			return templateList.get(random.nextInt(templateList.size()));
		}
		return null;
	}

	public static String metricsToString(Map<String, Object> metrics) {
		return metrics.entrySet().stream()
				.map(entry -> " - " + entry.getKey() + ": " + objectToString(entry.getValue()))
				.collect(Collectors.joining("\n", "\n", "\n"));
	}

	public static String objectToString(Object obj) {
		if (obj == null) {
			return "null";
		}
		if (obj instanceof Number num) {
			return BigDecimal.valueOf(num.doubleValue())
				.setScale(4, RoundingMode.FLOOR)
				.toString();
		}
		return obj.toString();
	}
}
