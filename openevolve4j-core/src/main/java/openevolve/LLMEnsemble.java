package openevolve;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;
import openevolve.OpenEvolveConfig.LLM;

/**
 * An ensemble of Large Language Model (LLM) chat clients that provides random sampling. This class
 * manages multiple ChatClient instances and allows random selection for load balancing.
 */
public class LLMEnsemble {
	private final Map<String, ChatClient> llms = new HashMap<>();
	private final String[] keys;
	private final Random random;

	public LLMEnsemble(Random random, LLM llm, OpenAiApi openAiApi) {
		this.random = random;
		this.keys = llm.models().stream().map(OpenAiChatOptions::getModel).toArray(String[]::new);
		for (var config : llm.models()) {
			var chatModel =
					OpenAiChatModel.builder().openAiApi(openAiApi)
							.retryTemplate(RetryTemplate.builder()
									.retryOn(Throwable.class)
									.exponentialBackoff(Duration.ofSeconds(10), 2,
											Duration.ofSeconds(30))
									.build())
							.defaultOptions(config).build();
			llms.put(config.getModel(), ChatClient.builder(chatModel).build());
		}
	}

	public Map.Entry<String, ChatClient> sample() {
		var key = keys[random.nextInt(keys.length)];
		return Map.entry(key, llms.get(key));
	}

	public Iterator<ChatClient> iterator() {
		return llms.values().iterator();
	}
}
