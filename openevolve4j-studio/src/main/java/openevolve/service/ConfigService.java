package openevolve.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import openevolve.Application;
import openevolve.OpenEvolveConfig;

@Service
public class ConfigService {
    
	private static final Logger log = org.slf4j.LoggerFactory.getLogger(ConfigService.class);

	private final Map<String, OpenEvolveConfig> configs = new ConcurrentHashMap<>();
    private final Path path;

	public ConfigService(Application.Configuration config) {
		this.path = config.path();
	}

    public Map<String, OpenEvolveConfig> reload() {
        configs.clear();
        configs.putAll(loadConfigs(path));
        return configs;
    }

	public OpenEvolveConfig save(OpenEvolveConfig config, String id) {
		configs.put(id, config);
		return config;
	}

	public OpenEvolveConfig findById(String id) {
		return configs.get(id);
	}

    public OpenEvolveConfig delete(String id) {
        return configs.remove(id);
    }

	public static Map<String, OpenEvolveConfig> loadConfigs(Path problemsPath) {
        // structure: /problems/{problem}/config.yaml
        Map<String, OpenEvolveConfig> configs = new HashMap<>();
        Set<String> names = Set.of("config.yaml", "config.yml");
        try {
            Files.walk(problemsPath, 2)
                    .filter(path -> names.contains(path.getFileName().toString()))
                    .forEach(path -> {
                        try {
                            configs.put(path.getParent().getFileName().toString(), OpenEvolveConfig.fromFile(path));
                        } catch (Exception e) {
							log.warn("Failed to load config from {} with error: {}", path, e.getMessage());
                        }
                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return configs;
    }
}
