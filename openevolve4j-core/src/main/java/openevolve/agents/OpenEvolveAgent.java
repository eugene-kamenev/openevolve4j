package openevolve.agents;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import openevolve.Constants;
import openevolve.domain.EvolutionStep;
import openevolve.domain.Solution;
import openevolve.domain.SourceTree;
import openevolve.util.CodeParsingUtils;
import openevolve.util.CodeParsingUtils.DiffBlock;

public class OpenEvolveAgent implements Function<EvolutionStep<Solution<SourceTree>>, SourceTree> {

	private static final Logger log = org.slf4j.LoggerFactory.getLogger(OpenEvolveAgent.class);

	private String researchReport;
	private final Map<String, PromptTemplate> templates;
	private final ChatClient client;
	private final String model;

	public OpenEvolveAgent(Map<String, PromptTemplate> templates, ChatClient client,
			String model) {
		this.templates = templates;
		this.client = client;
		this.model = model;
	}

	@Override
	public SourceTree apply(EvolutionStep<Solution<SourceTree>> step) {
		List<Solution<SourceTree>> inspirations = Stream
				.of(step.ancestors().stream(), step.children().stream())
				.flatMap(Function.identity()).distinct().toList();
		var prompt = generatePrompt(step.parent(), inspirations);
		String response = null;
		if (log.isDebugEnabled()) {
			log.debug("Doing request to LLM model: {}", model);
		}
		response = client.prompt(prompt).call().content();
		if (response == null || response.isBlank()) {
			log.error("LLM ({} ) returned empty response", model);
			return null;
		}
		var llmRequest = promptToMap(prompt);
		var metadata = Map.of("llmModel", model, "llmRequest", llmRequest, "llmResponse",
				response, "fullRewrite", step.parent().data().fullRewrite());
		if (log.isDebugEnabled()) {
			log.debug("LLM model {} request done", model);
		}
		var newSolution = newSolution(step.parent(), metadata);
		if (newSolution == null) {
			log.error("Failed to parse LLM ({} ) response: {}", model, metadata);
			return null;
		}
		return newSolution;
	}

	private Prompt generatePrompt(Solution<SourceTree> parent,
			List<Solution<SourceTree>> inspirations) {
		var taskTmpl = getTemplate(Constants.TASK).getTemplate();
		var systemMessageTmpl =
				new SystemPromptTemplate(getTemplate(Constants.SYSTEM_DEFAULT).getTemplate());
		var userMessageTmpl = getTemplate(Constants.USER_FULL_REWRITE);
		if (!parent.data().fullRewrite()) {
			userMessageTmpl = getTemplate(Constants.USER_DIFF);
		}

		var solutionXml = renderSolution("Current Candidate", parent);
		return new Prompt(systemMessageTmpl.createMessage(),
				userMessageTmpl.createMessage(Map.of("solution", solutionXml, "task", taskTmpl,
						"parents", "", "research",
						researchReport != null ? researchReport : "")));
	}
	
	private String renderSolution(String name, Solution<SourceTree> solution) {
		var solutionTmpl = getTemplate(Constants.SOLUTION);
		var solutionFileTmpl = getTemplate(Constants.SOLUTION_FILE);
		String files = solution.data().files().entrySet().stream().map(
				e -> solutionFileTmpl.render(Map.of("filename", e.getKey().toString(), "content", e.getValue())))
				.collect(Collectors.joining("\n"));
		return solutionTmpl.render(Map.of("files", files, "name", name, "metrics", metricsToString(solution.fitness())));
	}

	public static SourceTree newSolution(Solution<SourceTree> parent,
			Map<String, Object> metadata) {

		var llmResponse = (String) metadata.get("llmResponse");
		if (llmResponse == null || llmResponse.isBlank()) {
			return null;
		}
		Map<Path, String> updatedFiles = null;
		Map<Path, List<DiffBlock>> changes = null;
		if (parent.data().fullRewrite()) {
			/*
			trace("XML parser found {} solution(s)", found.size());
			if (!found.isEmpty()) {
				newSolution = applyChanges(found.get(0), parent.solution().files());
				trace("Applied full-rewrite changes; new solution file count={}", newSolution.size());
			} */
		} else {
			var changeList = CodeParsingUtils.extractChanges(llmResponse, parent.data().files().keySet()
					.stream().map(Path::toString).toList());
			if (!changeList.isEmpty()) {
				var changeMap = new LinkedHashMap<Path, List<DiffBlock>>();
				for (var change : changeList) {
					changeMap.computeIfAbsent(Path.of(change.getFile()), _ -> new ArrayList<>())
							.add(change);
				}
				updatedFiles = new LinkedHashMap<Path, String>();
				for (var entry : changeMap.entrySet()) {
					var filePath = entry.getKey();
					var diffs = entry.getValue();
					var newContent = CodeParsingUtils.applyDiff(parent.data().files().get(filePath), diffs);
					updatedFiles.put(filePath, newContent);
					if (newContent.equals(parent.data().files().get(filePath))) {
						log.warn("File content did not change: {}", llmResponse);
					}
				}
				changes = changeMap;
			}
		}
		if (updatedFiles != null && !updatedFiles.isEmpty()) {
			return new SourceTree(updatedFiles, metadata);
		}
		return null;
	}

	public PromptTemplate getTemplate(String key) {
		return templates.get(key);
	}

	public static String objectToString(Object obj) {
		if (obj == null) {
			return "null";
		}
		if (obj instanceof Number num) {
			return BigDecimal.valueOf(num.doubleValue()).setScale(4, RoundingMode.FLOOR).toString();
		}
		return obj.toString();
	}

	protected Map<String, String> promptToMap(Prompt prompt) {
		var map = new LinkedHashMap<String, String>();
		prompt.getInstructions().stream()
				.forEach(i -> map.put(i.getMessageType().name().toLowerCase(), i.getText()));
		return map;
	}

	public static String metricsToString(Map<String, Object> metrics) {
		return metrics.entrySet().stream()
				.map(entry -> " - " + entry.getKey() + ": " + objectToString(entry.getValue()))
				.collect(Collectors.joining("\n", "\n", "\n"));
	}
}
