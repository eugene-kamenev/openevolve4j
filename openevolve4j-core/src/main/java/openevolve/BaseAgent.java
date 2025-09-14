package openevolve;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlCData;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import openevolve.mapelites.Repository.Solution;
import openevolve.util.CodeParsingUtils;
import openevolve.util.StreamingXmlParser;
import openevolve.util.CodeParsingUtils.DiffBlock;

public abstract class BaseAgent {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BaseAgent.class);


	protected final Map<String, List<PromptTemplate>> templates;
	protected final LLMEnsemble llmEnsemble;
	protected final Random random;

	protected final String SOLUTION_EXAMPLE;

	public BaseAgent(Map<String, List<PromptTemplate>> templates, LLMEnsemble llmEnsemble,
			Random random) {
		this.templates = templates;
		this.llmEnsemble = llmEnsemble;
		this.random = random;
		this.SOLUTION_EXAMPLE = renderXml(
				new SolutionXml(List.of(new SolutionFile("/path_to_solution_file", "New content")),
						null),
				"Solution");
		// trace construction and a short preview of example length
		trace("BaseAgent constructed; solution example length={}", SOLUTION_EXAMPLE == null ? 0
				: SOLUTION_EXAMPLE.length());
	}

	public EvolveSolution newSolution(Solution<EvolveSolution> parent,
			Map<String, Object> metadata) {

		trace("newSolution called for parent id={}", parent == null ? "null" : parent.id());
		var llmResponse = (String) metadata.get("llmResponse");
		trace("llmResponse present=%b", llmResponse != null && !llmResponse.isBlank());
		if (llmResponse == null || llmResponse.isBlank()) {
			trace("No llmResponse provided, returning null");
			return null;
		}
		Map<Path, String> newSolution = null;
		Map<Path, List<Change>> changes = null;
		if (parent.solution().fullRewrite()) {
			trace("Parent requests full rewrite; attempting XML parse of LLM response");
			var xmlParser = new StreamingXmlParser<>(Constants.XML_MAPPER,
					new TypeReference<SolutionXml>() {});
			var found = new ArrayList<SolutionXml>();
			xmlParser.consume(found::add);
			xmlParser.feedText(llmResponse);
			trace("XML parser found {} solution(s)", found.size());
			if (!found.isEmpty()) {
				newSolution = applyChanges(found.get(0), parent.solution().files());
				trace("Applied full-rewrite changes; new solution file count={}", newSolution.size());
			}
		} else {
			trace("Parent requests diffs (not full rewrite); extracting changes from LLM response");
			var changeList = CodeParsingUtils.extractChanges(llmResponse, parent.solution().files().keySet()
					.stream().map(Path::toString).toList());
			trace("Extracted {} change blocks from LLM", changeList.size());
			if (!changeList.isEmpty()) {
				var changeMap = new LinkedHashMap<Path, List<DiffBlock>>();
				for (var change : changeList) {
					changeMap.computeIfAbsent(Path.of(change.getFile()), _ -> new ArrayList<>())
							.add(change);
				}
				newSolution = new LinkedHashMap<Path, String>();
				for (var entry : changeMap.entrySet()) {
					var filePath = entry.getKey();
					var diffs = entry.getValue();
					trace("Applying {} diffs to file {}", diffs.size(), filePath);
					var newContent = CodeParsingUtils.applyDiff(parent.solution().files().get(filePath), diffs);
					newSolution.put(filePath, newContent);
				}
				trace("Diff application complete; new solution file count={}", newSolution.size());
			}
		}
		if (newSolution != null && !newSolution.isEmpty()) {
			trace("Constructing EvolveSolution with {} files", newSolution.size());
			var evolvedSolution = new EvolveSolution(parent.id(), Instant.now(), newSolution,
					changes, parent.fitness(), metadata, parent.solution().fullRewrite());
			trace("EvolveSolution created for parent id={}", parent.id());
			return evolvedSolution;
		}
		trace("No new solution produced, returning null");
		return null;
	}

	public PromptTemplate getTemplate(String key) {
		var templateList = templates.get(key);
		if (templateList != null && !templateList.isEmpty()) {
			var chosen = templateList.get(random.nextInt(templateList.size()));
			trace("Selected template for key={} (list size={})", key, templateList.size());
			return chosen;
		}
		trace("No template found for key={}", key);
		return null;
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
		trace("Converted Prompt to map with {} entries", map.size());
		return map;
	}

	protected List<SolutionXml> convert(List<Solution<EvolveSolution>> inspirations) {
		trace("Converting {} inspirations to SolutionXml", inspirations == null ? 0 : inspirations.size());
		return inspirations.stream().map(sol -> {
			var files = sol.solution().files().entrySet().stream()
					.map(entry -> new SolutionFile(entry.getKey().toString(), entry.getValue()))
					.toList();
			return new SolutionXml(files, sol.fitness());
		}).toList();
	}

	protected String renderXml(Object obj, String rootName) {
		try {
			var writer = Constants.XML_MAPPER.writerWithDefaultPrettyPrinter();
			if (rootName != null && !rootName.isBlank()) {
				writer = writer.withRootName(rootName);
			}
			var result = writer.writeValueAsString(obj);
			trace("Rendered XML for rootName={}, output length={}", rootName, result == null ? 0 : result.length());
			return result;
		} catch (JsonProcessingException e) {
			System.err.println("Error rendering XML: " + e.getMessage());
		}
		return null;
	}

	@JacksonXmlRootElement(localName = "Solutions")
	public record SolutionsXml(@JacksonXmlElementWrapper(useWrapping = false) // disables extra
																				// <SolutionXml>
																				// wrapper
	@JacksonXmlProperty(localName = "Solution") List<SolutionXml> solutions) {
	}

	@JacksonXmlRootElement(localName = "Solution")
	public record SolutionXml(List<SolutionFile> files, Map<String, Object> performance) {
	}

	@JacksonXmlRootElement(localName = "File")
	public record SolutionFile(String path, @JacksonXmlCData String content) {
	}

	@JacksonXmlRootElement(localName = "Changes")
	public record ChangesXml(@JacksonXmlElementWrapper(useWrapping = false) @JacksonXmlProperty(
			localName = "Change") List<Change> changes) {
	}

	@JacksonXmlRootElement(localName = "Change")
	public record Change(String file, @JacksonXmlProperty(localName = "SearchAndReplace")
			@JacksonXmlCData String searchAndReplace) {
	}

	public static Map<Path, String> applyChanges(SolutionXml solution, Map<Path, String> base) {
		if (solution == null || solution.files() == null || solution.files().isEmpty()) {
			trace("applyChanges called but solution is empty; returning base unchanged");
			return base;
		}
		var files = new LinkedHashMap<Path, String>();
		files.putAll(base);
		for (var file : solution.files()) {
			var filePath = Path.of(file.path());
			files.put(filePath, file.content());
			trace("applyChanges updated file {} (length={})", filePath, file.content() == null ? 0 : file.content().length());
		}
		trace("applyChanges completed; total files after apply={}", files.size());
		return files;
	}

	// Add static trace helper that checks isTraceEnabled before logging
	private static void trace(String format, Object... args) {
		if (log.isTraceEnabled()) {
			log.trace(format, args);
		}
	}
}
