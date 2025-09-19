package openevolve;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import openevolve.mapelites.Population.Solution;

public class OpenEvolveAgent extends BaseAgent implements Function<EvolveStep, EvolveSolution> {

	private static final Logger log = org.slf4j.LoggerFactory.getLogger(OpenEvolveAgent.class);

	public OpenEvolveAgent(Map<String, List<PromptTemplate>> templates, LLMEnsemble llmEnsemble,
			Random random) {
		super(templates, llmEnsemble, random);
	}

	@Override
	public EvolveSolution apply(EvolveStep step) {
		var client = llmEnsemble.sample();
		List<Solution<EvolveSolution>> inspirations = Stream
				.of(step.topSolutions().stream(), step.inspirations().stream(),
						step.previousSolutions().stream())
				.flatMap(Function.identity()).distinct().toList();
		var prompt = generatePrompt(step.parent(), inspirations);
		String response = null;
		if (log.isTraceEnabled()) {
			log.trace("Doing request to LLM model", client.getKey());
		}
		response = client.getValue().prompt(prompt).call().content();
		var llmRequest = promptToMap(prompt);
		var metadata = Map.of("llmModel", client.getKey(), "llmRequest", llmRequest, "llmResponse",
				response, "fullRewrite", step.parent().solution().fullRewrite());
		if (log.isTraceEnabled()) {
			log.trace("LLM model {} request done", client.getKey());
		}
		var newSolution = this.newSolution(step.parent(), metadata);
		if (log.isTraceEnabled()) {
			log.trace("Parsing LLM model {} response", client.getKey());
		}
		log.trace("LLM model {} produced solution", client.getKey());
		if (newSolution == null) {
			log.error("Failed to parse LLM ({} ) response: {}", client.getKey(), metadata);
			return null;
		}
		return newSolution;
	}

	private Prompt generatePrompt(Solution<EvolveSolution> parent,
			List<Solution<EvolveSolution>> inspirations) {
		var taskTmpl = getTemplate(Constants.TASK).getTemplate();
		var systemMessageTmpl =
				new SystemPromptTemplate(getTemplate(Constants.SYSTEM_DEFAULT).getTemplate());
		var userMessageTmpl = getTemplate(Constants.USER_FULL_REWRITE);
		var format = SOLUTION_EXAMPLE;
		if (!parent.solution().fullRewrite()) {
			userMessageTmpl = getTemplate(Constants.USER_DIFF);
		}
		var solutionXml = renderXml(convert(List.of(parent)).get(0), "Solution");
		var inspirationsXml = renderXml(new SolutionsXml(convert(inspirations)), "Solutions");
		return new Prompt(systemMessageTmpl.createMessage(),
				userMessageTmpl.createMessage(
						Map.of("format", format, "solution", solutionXml, "task", taskTmpl,
								"parents", inspirationsXml != null ? inspirationsXml : "")));
	}
}
