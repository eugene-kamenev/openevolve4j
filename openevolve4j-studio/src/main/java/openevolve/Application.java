package openevolve;

import java.io.Serializable;
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
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import openevolve.db.DbHandler;
import openevolve.db.DbHandlers.LLMModelDbHandler;
import openevolve.domain.FitnessAware;
import openevolve.domain.SourceTree;
import openevolve.studio.domain.LLMModel;
import openevolve.studio.domain.Event.Solution;
import openevolve.studio.domain.Event.Payload;
import openevolve.studio.domain.Event.Progress;
import openevolve.studio.domain.Event.Run;

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
         mapper.registerSubtypes(
            new NamedType(SourceTree.class, "SOURCE_TREE"),
			new NamedType(Solution.class, "SOLUTION"),
			new NamedType(Run.class, "EVOLUTION_RUN"),
			new NamedType(Progress.class, "PROGRESS")
        );
		mapper.addMixIn(Payload.class, JacksonMixIn.TypeInfo.class);
		mapper.addMixIn(FitnessAware.Data.class, JacksonMixIn.TypeInfo.class);
		return mapper;
	}

	@Bean
	RestClient.Builder restClientBuilder() {
		var builder = RestClient.builder();
		ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
				.withConnectTimeout(Duration.ofSeconds(30)).withReadTimeout(Duration.ofMinutes(4));
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
