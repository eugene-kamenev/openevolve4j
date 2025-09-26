package openevolve.agents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import openevolve.deepresearch.tools.WebSearchAndFetch;

public class DeepResearchAgent {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DeepResearchAgent.class);

	// Add: public static constants for template names
	public static final String SYSTEM_DEEPRESEARCH_REPORT = "system_deepresearch_report";
	public static final String USER_DEEPRESEARCH_REPORT = "user_deepresearch_report";

	public static final String SYSTEM_DEEPRESEARCH_ENSURE_CHECK = "system_deepresearch_ensure_check";
	public static final String USER_DEEPRESEARCH_ENSURE_CHECK = "user_deepresearch_ensure_check";

	public static final String SYSTEM_DEEPRESEARCH_FIND_SEGMENTS = "system_deepresearch_find_segments";
	public static final String USER_DEEPRESEARCH_FIND_SEGMENTS = "user_deepresearch_find_segments";

	public static final String SYSTEM_DEEPRESEARCH_SEARCH_PHRASES = "system_deepresearch_search_phrases";
	public static final String USER_DEEPRESEARCH_SEARCH_PHRASES = "user_deepresearch_search_phrases";

	public static final String SYSTEM_DEEPRESEARCH_TOPICS = "system_deepresearch_topics";
	public static final String USER_DEEPRESEARCH_TOPICS = "user_deepresearch_topics";

	public static final String SYSTEM_DEEPRESEARCH_DECOMPOSITION = "system_deepresearch_decomposition";
	public static final String USER_DEEPRESEARCH_DECOMPOSITION = "user_deepresearch_decomposition";

	public static final String SYSTEM_DEEPRESEARCH_VALIDATE = "system_deepresearch_validate";
	public static final String USER_DEEPRESEARCH_VALIDATE = "user_deepresearch_validate";

	public record Request(String query) {
	
	}

	public record Research(String error, String taskPrompt, String formatPrompt, List<String> urls, Map<String, List<String>> topicRelevantSegments) {
		public Research(String error) {
			this(error, null, null, List.of(), Map.of());
		}
	}

	// Change: carry formatted report text (and optional error)
	public record Report(String error, String report) {
		public Report(String error) {
			this(error, null);
		}
	}

	private final WebSearchAndFetch webSearchAndFetch;
	private final ChatClient researchClient;
	private final Map<String, List<PromptTemplate>> templates;
	private final int maxTopics;
	private final int maxSearchPhrases;
	private final Random random;

	public DeepResearchAgent(
			WebSearchAndFetch webSearchAndFetch,
			ChatClient researchClient,
			Map<String, List<PromptTemplate>> templates,
			int maxTopics, int maxSearchPhrases, Random random) {
		this.webSearchAndFetch = webSearchAndFetch;
		this.researchClient = researchClient;
		this.templates = templates;
		this.maxTopics = maxTopics;
		this.maxSearchPhrases = maxSearchPhrases;
		this.random = random;
	}

	public Research doResearch(Request request) {
		if (request == null || request.query() == null || request.query().isBlank()) {
			if (log.isDebugEnabled()) {
				log.debug("doResearch: empty request");
			}
			return new Research("Empty request");
		}
		if (log.isDebugEnabled()) {
			log.debug("doResearch: start, query={}", abbr(request.query(), 200));
		}
		if (!validateRequestQuery(request.query())) {
			if (log.isDebugEnabled()) {
				log.debug("doResearch: query validation failed");
			}
			return new Research("Invalid query");
		}

		var taskFormat = decomposePrompt(request.query());
		if (log.isDebugEnabled()) {
			log.debug("doResearch: decomposition parts={}", taskFormat.length);
		}
		if (taskFormat.length != 2) {
			return new Research("Could not decompose the query into task and format");
		}

		var topics = generateTopics(taskFormat[0]);
		if (log.isDebugEnabled()) {
			log.debug("doResearch: topics generated count={}", topics.size());
		}
		if (topics.isEmpty()) {
			return new Research("Could not generate research topics");
		}

		var topicRelevantSegments = new HashMap<String, List<String>>();
		var searchResultUrls = new ArrayList<String>();

		for (var topic : topics) {
			if (log.isDebugEnabled()) {
				log.debug("doResearch: processing topic='{}'", topic);
			}
			var searchPhrases = generateSearchPhrases(topic, request.query());
			if (log.isDebugEnabled()) {
				log.debug("doResearch: topic='{}' searchPhrases count={}", topic, searchPhrases.size());
			}
			var topicSegments = new ArrayList<String>();
			topicRelevantSegments.put(topic, topicSegments);

			for (var searchPhrase : searchPhrases) {
				if (log.isDebugEnabled()) {
					log.debug("doResearch: searching phrase='{}'", searchPhrase);
				}
				try {
					var searchResponse = webSearchAndFetch.search(searchPhrase).block();
					if (searchResponse == null || searchResponse.results() == null || searchResponse.results().isEmpty()) {
						if (log.isDebugEnabled()) {
							log.debug("doResearch: no results for phrase='{}'", searchPhrase);
						}
						continue;
					}

					// Only process NEW (original) results, just like the Python logic
					var originalResults = searchResponse.results().stream()
						.filter(r -> r.url() != null && !searchResultUrls.contains(r.url()))
						.toList();

					if (log.isDebugEnabled()) {
						log.debug("doResearch: results total={}, original new={}", searchResponse.results().size(), originalResults.size());
					}

					// Register new URLs globally
					for (var result : originalResults) {
						searchResultUrls.add(result.url());
					}

					// Extract relevant segments for newly added results
					for (var result : originalResults) {
						var urlIndex = searchResultUrls.indexOf(result.url());
						var segments = findSegments(request.query(), topic, result.content(), urlIndex);
						if (log.isDebugEnabled()) {
							log.debug("doResearch: urlIndex={} segments found={}", urlIndex, segments.size());
						}
						topicSegments.addAll(segments);
					}
				} catch (Exception e) {
					log.warn("doResearch: search error for phrase='{}': {}", searchPhrase, e.toString());
					// Continue on individual search errors
				}
			}
		}

		if (log.isDebugEnabled()) {
			log.debug("doResearch: done, urls collected={}, topics with segments={}", searchResultUrls.size(), topicRelevantSegments.size());
		}
		return new Research(null, taskFormat[0], taskFormat[1], searchResultUrls, topicRelevantSegments);
	}

	public Report doReport(Research researchResponse) {
		if (researchResponse.error() != null) {
			if (log.isDebugEnabled()) {
				log.debug("doReport: input response has error='{}'", researchResponse.error());
			}
			return new Report(researchResponse.error());
		}
		if (log.isDebugEnabled()) {
			log.debug("doReport: start, topics={}, urls={}", researchResponse.topicRelevantSegments().size(), researchResponse.urls().size());
		}
		// Recompute task and format from the original request (align with Python flow)
		var taskPrompt = researchResponse.taskPrompt();
		var formatPrompt = researchResponse.formatPrompt();

		var topicRelevantSegments = researchResponse.topicRelevantSegments();
		var searchResultUrls = researchResponse.urls();

		// Build a compact text block with all topic segments
		var topicSegmentsText = topicRelevantSegments.entrySet().stream()
			.map(e -> "## Topic: " + e.getKey() + "\n" + String.join("\n", e.getValue()))
			.collect(Collectors.joining("\n"));

		// Step 1: Produce the report
		if (log.isDebugEnabled()) {
			log.debug("doReport: generating report (segments text length={})", topicSegmentsText.length());
		}
		var reportPrompt = buildPrompt(
			SYSTEM_DEEPRESEARCH_REPORT,
			USER_DEEPRESEARCH_REPORT,
			Map.of(
				"taskPrompt", taskPrompt,
				"formatPrompt", formatPrompt,
				"topicSegments", topicSegmentsText
			)
		);
		var report = completion(reportPrompt);
		if (log.isDebugEnabled()) {
			log.debug("doReport: report generated length={}", report == null ? -1 : report.length());
		}
		if (report == null || report.isBlank()) {
			return new Report("Could not produce report");
		}

		// Step 2: Ensure formatting/consistency
		if (log.isDebugEnabled()) {
			log.debug("doReport: running ensure/consistency check");
		}
		var ensurePrompt = buildPrompt(
			SYSTEM_DEEPRESEARCH_ENSURE_CHECK,
			USER_DEEPRESEARCH_ENSURE_CHECK,
			Map.of(
				"taskPrompt", taskPrompt,
				"formatPrompt", formatPrompt,
				"report", report
			)
		);
		var consistent = completion(ensurePrompt);
		if (consistent == null || consistent.isBlank()) {
			if (log.isDebugEnabled()) {
				log.debug("doReport: ensure/consistency empty, falling back to original report");
			}
			consistent = report;
		} else if (log.isDebugEnabled()) {
			log.debug("doReport: ensure/consistency produced length={}", consistent.length());
		}

		// Step 3: Append references (index markers and footnotes)
		var sb = new StringBuilder(consistent);
		sb.append("\n\n---\n");
		for (int i = 0; i < searchResultUrls.size(); i++) {
			var url = searchResultUrls.get(i);
			// Use URL as link text (title may not be available from the search result object)
			sb.append(" - [[").append(i).append("]] [").append(url).append("][").append(i).append("]\n");
		}
		sb.append("\n");
		for (int i = 0; i < searchResultUrls.size(); i++) {
			sb.append("[").append(i).append("]: ").append(searchResultUrls.get(i)).append("\n");
		}
		if (log.isDebugEnabled()) {
			log.debug("doReport: references appended count={}", searchResultUrls.size());
		}

		return new Report(null, sb.toString());
	}

	private List<String> findSegments(String prompt, String topic, String content, int urlIndex) {
		if (content == null || content.isBlank()) {
			if (log.isDebugEnabled()) {
				log.debug("findSegments: empty content for urlIndex={}, topic='{}'", urlIndex, topic);
			}
			return List.of();
		}
		if (log.isDebugEnabled()) {
			log.debug("findSegments: topic='{}', urlIndex={}, content length={}", topic, urlIndex, content.length());
		}
		var llmPrompt = buildPrompt(SYSTEM_DEEPRESEARCH_FIND_SEGMENTS, USER_DEEPRESEARCH_FIND_SEGMENTS,
				Map.of("prompt", prompt, "topic", topic, "searchResult", content));
		var answer = completion(llmPrompt);
		if (answer == null || answer.isBlank()) {
			if (log.isDebugEnabled()) {
				log.debug("findSegments: no segments found");
			}
			return List.of();
		}
		var segments = answer.lines()
			.map(String::trim)
			.filter(s -> !s.isBlank())
			.map(s -> "[[" + urlIndex + "]] " + s)
			.collect(Collectors.toList());
		if (log.isDebugEnabled()) {
			log.debug("findSegments: segments count={}", segments.size());
		}
		return segments;
	}

	private List<String> generateSearchPhrases(String topic, String prompt) {
		if (log.isDebugEnabled()) {
			log.debug("generateSearchPhrases: topic='{}'", topic);
		}
		var llmPrompt = buildPrompt(SYSTEM_DEEPRESEARCH_SEARCH_PHRASES, USER_DEEPRESEARCH_SEARCH_PHRASES,
				Map.of("topic", topic, "prompt", prompt, "maxSearchPhrases", maxSearchPhrases));
		var answer = completion(llmPrompt);
		if (answer == null || answer.isBlank()) {
			if (log.isDebugEnabled()) {
				log.debug("generateSearchPhrases: empty answer");
			}
			return List.of();
		}
		var phrases = answer.lines()
			.map(String::trim)
			.filter(s -> !s.isBlank())
			.collect(Collectors.toList());
		Collections.shuffle(phrases, random);
		var limited = phrases.stream().limit(maxSearchPhrases).toList();
		if (log.isDebugEnabled()) {
			log.debug("generateSearchPhrases: produced={} limited={}", phrases.size(), limited.size());
		}
		return limited;
	}

	private List<String> generateTopics(String taskPrompt) {
		if (log.isDebugEnabled()) {
			log.debug("generateTopics: taskPrompt={}", abbr(taskPrompt, 200));
		}
		var llmPrompt = buildPrompt(SYSTEM_DEEPRESEARCH_TOPICS, USER_DEEPRESEARCH_TOPICS,
				Map.of("prompt", taskPrompt, "maxTopics", maxTopics));
		var answer = completion(llmPrompt);
		if (answer == null || answer.isBlank()) {
			if (log.isDebugEnabled()) {
				log.debug("generateTopics: empty answer");
			}
			return List.of();
		}
		var topics = answer.lines()
			.map(String::trim)
			.filter(s -> !s.isBlank())
			.collect(Collectors.toList());
		Collections.shuffle(topics, random);
		var limited = topics.stream().limit(maxTopics).toList();
		if (log.isDebugEnabled()) {
			log.debug("generateTopics: produced={} limited={}", topics.size(), limited.size());
		}
		return limited;
	}

	private String[] decomposePrompt(String prompt) {
		if (log.isDebugEnabled()) {
			log.debug("decomposePrompt: prompt={}", abbr(prompt, 200));
		}
		var llmPrompt = buildPrompt(SYSTEM_DEEPRESEARCH_DECOMPOSITION, USER_DEEPRESEARCH_DECOMPOSITION,
				Map.of("prompt", prompt));
		var answer = completion(llmPrompt);
		if (answer == null || answer.isBlank()) {
			if (log.isDebugEnabled()) {
				log.debug("decomposePrompt: empty answer");
			}
			return new String[0];
		}
		var parts = answer.split("\n\n");
		if (log.isDebugEnabled()) {
			log.debug("decomposePrompt: parts={}, total length={}", parts.length, answer.length());
		}
		return parts;
	}

	private boolean validateRequestQuery(String prompt) {
		if (log.isDebugEnabled()) {
			log.debug("validateRequestQuery: prompt={}", abbr(prompt, 200));
		}
		var llmPrompt = buildPrompt(SYSTEM_DEEPRESEARCH_VALIDATE, USER_DEEPRESEARCH_VALIDATE,
				Map.of("prompt", prompt));
		var answer = completion(llmPrompt);
		var ok = isOutputPositive(answer);
		if (log.isDebugEnabled()) {
			log.debug("validateRequestQuery: result={} (raw='{}')", ok, abbr(answer, 120));
		}
		return ok;
	}

	private boolean isOutputPositive(String answer) {
		return answer != null && (answer.toLowerCase().trim().contains("yes")
				|| answer.toLowerCase().trim().contains("true"));
	}

	private String completion(Prompt prompt) {
		long t0 = System.currentTimeMillis();
		var result = researchClient.prompt(prompt).call().content().trim();
		if (log.isDebugEnabled()) {
			long dt = System.currentTimeMillis() - t0;
			log.debug("completion: LLM call took {} ms, content length={}", dt, result == null ? -1 : result.length());
		}
		return result;
	}

	private Prompt buildPrompt(String systemTemplate, String userTemplate,
			Map<String, Object> variables) {
		if (log.isDebugEnabled()) {
			log.debug("buildPrompt: system='{}', user='{}', vars={}", systemTemplate, userTemplate, variables.keySet());
		}
		var system = new SystemPromptTemplate(templates.get(systemTemplate).get(0).getTemplate());
		var user = templates.get(userTemplate).get(0);
		var systemMessage = system.createMessage(variables);
		var userMessage = user.createMessage(variables);
		return new Prompt(systemMessage, userMessage);
	}

	// Helper for safe logging of large strings
	private String abbr(String s, int max) {
		if (s == null) return "null";
		var t = s.trim();
		if (t.length() <= max) return t;
		return t.substring(0, Math.max(0, max)) + "...(" + t.length() + " chars)";
	}
}
