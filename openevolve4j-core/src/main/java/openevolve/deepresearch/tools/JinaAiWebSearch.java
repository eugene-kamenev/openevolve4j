package openevolve.deepresearch.tools;

import java.util.List;

import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

public class JinaAiWebSearch implements WebSearch {

    private final WebClient webClient;

    public JinaAiWebSearch(WebClient.Builder wBuilder, String token, boolean fetchContent) {
        this.webClient = wBuilder.clone()
            .baseUrl("https://s.jina.ai")
            .defaultHeaders(h -> {
                h.set("Accept", "application/json");
                if (StringUtils.hasText(token)) {
                    h.set("Authorization", "Bearer " + token);
                }
                if (fetchContent) {
                    h.set("X-Engine", "direct");
                    h.set("X-Return-Format", "markdown");
                } else {
                    h.set("X-Respond-With", "no-content");
                }
            })
            .build();
    }

    @Override
    public String name() {
        return "JinaAI";
    }

    @Override
    public Mono<Response> search(Request query) {
        return this.webClient.post()
            .body(BodyInserters.fromValue(query))
            .retrieve()
            .bodyToMono(JinaAiSearchResponse.class)
            .map(jina -> convert(jina.data()))
            .onErrorReturn(Response.empty());
    }

    private Response convert(List<JinaAiSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return Response.empty();
        }
        return new Response(results.stream()
            .map(result -> new Result(result.title(), result.url(), result.description(), result.content(), null))
            .toList());
    }
    
    record JinaAiSearchResponse(Integer code, Integer status, List<JinaAiSearchResult> data) {}
    record JinaAiSearchResult(String title, String url, String description, String content) {}

}
