package openevolve.deepresearch.tools;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

public class SearXNGWebSearch implements WebSearch {

    private final WebClient webClient;

    public SearXNGWebSearch(WebClient.Builder wBuilder, String url) {
        this.webClient = wBuilder.clone()
            .baseUrl(url)
            .defaultHeaders(h -> {
                h.set("Accept", "application/json");
                h.set("Content-Type", "application/x-www-form-urlencoded");
            })
            .build();
    }

    @Override
    public Mono<Response> search(Request query) {
        var data = new LinkedMultiValueMap<String, String>();
        data.add("q", query.query());
        data.add("category_general", "1");
        data.add("format", "json");
        data.add("language", query.language());
        data.add("safesearch", "0");
        return webClient.post()
            .uri("/search")
            .body(BodyInserters.fromFormData(data))
            .retrieve()
            .bodyToMono(Response.class)
            .onErrorReturn(Response.empty());
    }

    @Override
    public String name() {
        return "SearXNG";
    }
}
