package openevolve;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import openevolve.api.AlgorithmConfig;
import openevolve.db.DbHandler;
import openevolve.db.LLMModel;
import openevolve.db.DbHandlers.LLMModelDbHandler;
import openevolve.puct.LLMPuctTreeConfig;

@SpringBootApplication
@EnableConfigurationProperties(Application.Configuration.class)
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@ConfigurationProperties(prefix = "openevolve")
	public record Configuration(Path path, LiteLlm liteLlm) {
		public record LiteLlm(String apiUrl, String apiKey) {
		}
	}

	@Bean
	ObjectMapper objectMapper() {
		var mapper = Constants.OBJECT_MAPPER;
		// Restrict to AlgorithmConfig subtypes
        mapper.registerSubtypes(
            new NamedType(OpenEvolveConfig.class, "MAPELITES"),
            new NamedType(LLMPuctTreeConfig.class, "TREE")
        );
		mapper.addMixIn(AlgorithmConfig.class, JacksonMixIn.TypeInfo.class);
		return mapper;
	}

	@Bean
	RestClient.Builder restClientBuilder() {
		var builder = RestClient.builder();
		ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
				.withConnectTimeout(Duration.ofSeconds(30)).withReadTimeout(Duration.ofMinutes(3));
		return builder.requestFactory(ClientHttpRequestFactoryBuilder.reactor().build(settings));
	}

	@Bean
	OpenAiApi openAiApi(Configuration configuration, RestClient.Builder restClientBuilder) {
		return OpenAiApi.builder()
				.apiKey(configuration.liteLlm.apiKey())
				.baseUrl(configuration.liteLlm.apiUrl())
				.restClientBuilder(restClientBuilder)
				.build();
	}

	public static abstract class JacksonMixIn {
		@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    	static class TypeInfo {}
	}

	@Component
	public static class StartupListener {

		private final RestClient restClient;
		private final DbHandler<LLMModel> dbHandler;

		public StartupListener(Configuration configuration, DbHandler<LLMModel> dbHandler, RestClient.Builder restClientBuilder) {
			this.restClient = restClientBuilder.clone()
					.defaultHeader("Authorization", "Bearer " + configuration.liteLlm.apiKey())
					.baseUrl(configuration.liteLlm.apiUrl())
					.build();
			this.dbHandler = dbHandler;
		}

		@Order
		@EventListener(classes = ApplicationReadyEvent.class)
		void onStartup() {
			try {
				var response = restClient.get().uri("/models").retrieve().body(ModelResponse.class);
				var modelNames = response.data().stream().map(m -> (String) m.get("id")).toList();
				((LLMModelDbHandler) dbHandler).syncModels(modelNames).block();
			} catch (Exception e) {
				throw new RuntimeException("Failed to fetch models from LiteLLM API", e);
			}
		}

		public record ModelResponse(List<Map<String, Object>> data) { }
	}
}
