package openevolve.deepresearch.tools;

import reactor.core.publisher.Mono;

public interface WebFetch {
	Mono<String> fetch(String url);

	WebFetch NONE = _ -> Mono.just("No content fetched;");
}
