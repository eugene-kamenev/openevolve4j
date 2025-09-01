package openevolve.integration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.nio.file.Path;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import openevolve.Constants;
import openevolve.OpenEvolve;
import openevolve.OpenEvolveConfig;
import openevolve.mapelites.listener.MAPElitesLoggingListener;

public class OpenEvolveTest {
	
	@Test
	@Disabled
	void testLLM() {
		var config = loadConfig("/openevolve/llm_prompt_eval/config.yml");
		assertNotNull(config);
		var openEvolve = OpenEvolve.create(config, Constants.OBJECT_MAPPER);
		assertNotNull(openEvolve);
		openEvolve.addListener(new MAPElitesLoggingListener<>());
		openEvolve.run(config.mapelites().numIterations());
	}

	public OpenEvolveConfig loadConfig(String path) {
		try {
			var configPath = Path.of(getClass().getResource(path).toURI());
			return OpenEvolveConfig.fromFile(configPath);
		} catch (Throwable e) {
			throw new RuntimeException("Failed to load config from " + path, e);
		}
	}
}
