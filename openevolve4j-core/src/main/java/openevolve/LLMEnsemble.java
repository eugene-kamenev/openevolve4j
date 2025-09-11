package openevolve;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.http.client.reactive.ClientHttpConnectorBuilder;
import org.springframework.boot.http.client.reactive.ClientHttpConnectorSettings;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.Builder;
import openevolve.OpenEvolveConfig.LLM;
import reactor.util.retry.Retry;

/**
 * An ensemble of Large Language Model (LLM) chat clients that provides random sampling. This class
 * manages multiple ChatClient instances and allows random selection for load balancing.
 */
public class LLMEnsemble {
	private final Map<String, ChatClient> llms = new HashMap<>();
	private final String[] keys;
	private final Random random;

	public LLMEnsemble(Random random, LLM llm, RestClient.Builder restBuilder) {
		var openAiApi = OpenAiApi.builder().restClientBuilder(restBuilder).baseUrl(llm.apiUrl())
				.apiKey(llm.apiKey());
		this.random = random;
		this.keys = llm.models().stream().map(OpenAiChatOptions::getModel).toArray(String[]::new);
		for (var config : llm.models()) {
			var chatModel =
					OpenAiChatModel.builder().openAiApi(openAiApi.build())
							.retryTemplate(RetryTemplate.builder()
									.exponentialBackoff(Duration.ofSeconds(5), 2,
											Duration.ofSeconds(30))
									.build())
							.defaultOptions(config).build();
			llms.put(config.getModel(), ChatClient.builder(chatModel).build());
		}
	}

	public ChatClient sample() {
		return llms.get(keys[random.nextInt(keys.length)]);
	}

	public Iterator<ChatClient> iterator() {
		return llms.values().iterator();
	}
}
