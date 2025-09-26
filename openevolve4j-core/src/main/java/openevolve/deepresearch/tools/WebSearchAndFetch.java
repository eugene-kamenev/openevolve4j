package openevolve.deepresearch.tools;

import reactor.core.publisher.Mono;

public class WebSearchAndFetch {
	private final WebSearch webSearch;
	private final WebFetch webFetch;

	public WebSearchAndFetch(WebSearch webSearch, WebFetch webFetch) {
		this.webSearch = webSearch;
		this.webFetch = webFetch;
	}

	public Mono<WebSearch.Response> search(String searchTerm) {
		var searchResults = webSearch.search(new WebSearch.Request(searchTerm, null, "en"));
		return searchResults.flatMap(response -> {
			var fetchMonos = response.results().stream().map(result -> {
				// Only fetch if content is null; otherwise reuse existing result
				if (result.content() != null) {
					return Mono.just(result);
				}
				return webFetch.fetch(result.url())
						.map(content -> new WebSearch.Result(result.title(), result.url(),
								result.description(), content, null))
						.onErrorResume(_ -> Mono.just(new WebSearch.Result(result.title(),
								result.url(), result.description(), null, null)));
			}).toList();
			return Mono.zip(fetchMonos, fetchedResults -> {
				var results = new java.util.ArrayList<WebSearch.Result>();
				for (var obj : fetchedResults) {
					results.add((WebSearch.Result) obj);
				}
				return new WebSearch.Response(results);
			});
		});
	}
}
