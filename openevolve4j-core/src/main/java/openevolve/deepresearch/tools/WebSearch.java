package openevolve.deepresearch.tools;

import java.util.List;

import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonProperty;

import reactor.core.publisher.Mono;

public interface WebSearch {

    record Request(@JsonProperty("q") String query, @JsonProperty("gl") String country,
            @JsonProperty("hl") String language) {
        public Request(String query, String country, String language) {
            this.query = query;
            this.country = StringUtils.hasText(country) ? country : "US";
            this.language = StringUtils.hasText(language) ? language : "en";
        }
    }

    record Response(List<Result> results) {
        public static Response empty() {
            return new Response(List.of());
        }
    }

    record Result(String title, String url, String description, String content,
            @JsonProperty("raw_content") String rawContent) {
        public String content() {
            return rawContent != null ? rawContent : content;
        }
    }

    Mono<Response> search(Request query);

    String name();

    public static enum Type {
        SEARXNG, JINAAI, TAVILY
    }
}
