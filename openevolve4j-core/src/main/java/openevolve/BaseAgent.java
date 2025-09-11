package openevolve;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import org.springframework.ai.chat.prompt.PromptTemplate;
import openevolve.util.CodeParsingUtils;
import openevolve.util.SolutionUtil;
import openevolve.util.SolutionUtil.PathStructure;

public abstract class BaseAgent {

	protected final Map<String, List<PromptTemplate>> templates;
	protected final LLMEnsemble llmEnsemble;
	protected final Random random;

	public BaseAgent(Map<String, List<PromptTemplate>> templates, LLMEnsemble llmEnsemble, Random random) {
		this.templates = templates;
		this.llmEnsemble = llmEnsemble;
		this.random = random;
	}

	public EvolveSolution newSolution(EvolveStep step, List<Map<String, String>> llmRequest, String llmResponse) {
		String changes = null;
		String newSolution = null;
		var parent = step.parent();
		if (parent.solution().fullRewrite()) {
			newSolution = CodeParsingUtils.parseFullRewrite(llmResponse, parent.solution().language()).orElse(null);
			changes = "Full rewrite";
		} else if (llmResponse != null && !llmResponse.isBlank()) {
			var diffs = CodeParsingUtils.extractDiffs(llmResponse);
			if (!diffs.isEmpty()) {
				newSolution = CodeParsingUtils.applyDiff(SolutionUtil.toContent(parent.solution().files()), llmResponse);
				changes = CodeParsingUtils.formatDiffSummary(diffs);
			}
		}
		Map<String, Object> metadata = Map.of(
			"llmRequest", llmRequest,
			"llmResponse", llmResponse
		);
		if (newSolution != null) {
			var newTarget = PathStructure.applyChanges(newSolution, parent.solution().files());
			var evolvedSolution = new EvolveSolution(
				parent.id(),
				Instant.now(),
				newTarget,
				parent.solution().language(),
				changes,
				parent.fitness(),
				metadata,
				parent.solution().fullRewrite()
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
