package openevolve;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootApplication
@EnableConfigurationProperties(Application.Configuration.class)
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@ConfigurationProperties(prefix = "openevolve")
	public record Configuration(Path path, Map<String, OpenEvolveConfig> configs) {
	}

	@Bean
	ObjectMapper objectMapper() {
		return Constants.OBJECT_MAPPER;
	}

	@Bean
	RestClient.Builder restClientBuilder() {
		var builder = RestClient.builder();
		ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
				.withConnectTimeout(Duration.ofSeconds(30))
				.withReadTimeout(Duration.ofMinutes(6));
		// with retry
		return builder.requestFactory(ClientHttpRequestFactoryBuilder.reactor().build(settings));
	}
}
