package openevolve;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import openevolve.mapelites.Repository.Solution;
import openevolve.util.SolutionUtil;

public class OpenEvolveAgent extends BaseAgent implements Function<EvolveStep, EvolveSolution> {

	private final int numTopSolutions;
	private final int numDiverseSolutions;

	public OpenEvolveAgent(Map<String, List<PromptTemplate>> templates, LLMEnsemble llmEnsemble,
			Random random, int numTopSolutions, int numDiverseSolutions) {
		super(templates, llmEnsemble, random);
		this.numTopSolutions = numTopSolutions;
		this.numDiverseSolutions = numDiverseSolutions;
	}

	@Override
	public EvolveSolution apply(EvolveStep step) {
		var client = llmEnsemble.sample();
		var userMessageTmpl = getTemplate(Constants.USER_DIFF);
		var parent = step.parent();
		var solution = parent.solution();
		if (solution.fullRewrite()) {
			userMessageTmpl = getTemplate(Constants.USER_FULL_REWRITE);
		}
		var taskTmpl = getTemplate(Constants.TASK).getTemplate();
		var solutionTmpl = renderSolution(parent, "Current Solution");
		var parentsBuilder = new StringBuilder();
		int i = 1;
		List<Solution<EvolveSolution>> solutions = Stream
				.of(step.topSolutions().stream(), step.inspirations().stream(),
						step.previousSolutions().stream())
				.flatMap(Function.identity()).distinct().toList();
		if (!solutions.isEmpty()) {
			for (var p : solutions) {
				parentsBuilder.append(renderSolution(p, "Parent Solution " + i)).append("\n---\n");
				i++;
			}
		}
		var systemMessageTmpl =
				new SystemPromptTemplate(getTemplate(Constants.SYSTEM_DEFAULT).getTemplate());
		var userPrompt = userMessageTmpl.createMessage(Map.of("solution", solutionTmpl, "task",
				taskTmpl, "parents", parentsBuilder.toString()));
		var systemPrompt = systemMessageTmpl.createMessage();
		String response = null;
		int count = 0;
		while (response == null && count < 2) {
			try {
				response = client.prompt(new Prompt(systemPrompt, userPrompt)).call().content();
			} catch (Throwable t) {
				// ignored
			}
			count++;
		}
		return newSolution(step, response);
	}

	private String renderSolution(Solution<EvolveSolution> solution, String name) {
		return getTemplate(Constants.SOLUTION)
				.render(Map.of("code", SolutionUtil.toContent(solution.solution().files()),
						"language", solution.solution().language(), "metrics",
						metricsToString(solution.fitness()), "name", name));
	}
}
