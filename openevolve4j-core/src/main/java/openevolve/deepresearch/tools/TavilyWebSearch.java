package openevolve.deepresearch.tools;

import reactor.core.publisher.Mono;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

public class TavilyWebSearch implements WebSearch {
	private static final String apiUrl = "https://api.tavily.com/search";

	private final WebClient webClient;

	public TavilyWebSearch(String apiKey) {
		this.webClient = WebClient.builder()
				.baseUrl(apiUrl)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.build();
	}

	@Override
	public Mono<WebSearch.Response> search(WebSearch.Request request) {
		return webClient.post()
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(Map.of("query", request.query(), "include_raw_content", "text"))
				.retrieve()
				.bodyToMono(WebSearch.Response.class);
	}

	@Override
	public String name() {
		return "Tavily";
	}
}
