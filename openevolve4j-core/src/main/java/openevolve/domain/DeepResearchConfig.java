package openevolve.domain;

import openevolve.deepresearch.tools.WebSearch;

public record DeepResearchConfig(WebSearch.Type type, String apiKey) {
	
}
