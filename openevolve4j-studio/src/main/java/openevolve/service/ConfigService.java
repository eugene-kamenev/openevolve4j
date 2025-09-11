package openevolve.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.type.TypeReference;
import openevolve.Application;
import openevolve.Constants;
import openevolve.EvolveSolution;
import openevolve.OpenEvolveConfig;
import openevolve.mapelites.Repository.Solution;
import openevolve.util.Util;

@Service
public class ConfigService {
    
	private static final Logger log = org.slf4j.LoggerFactory.getLogger(ConfigService.class);

	private final Map<String, OpenEvolveConfig> configs = new ConcurrentHashMap<>();
    private final Map<String, List<Solution<EvolveSolution>>> solutions = new ConcurrentHashMap<>();
    private final Path path;

	public ConfigService(Application.Configuration config) {
		this.path = config.path();
	}

    public Map<String, OpenEvolveConfig> reload() {
        configs.clear();
        configs.putAll(loadConfigs(path));
        solutions.clear();
        solutions.putAll(loadSolutions(configs));
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

    public List<Solution<EvolveSolution>> getSolutions(String id) {
        return solutions.getOrDefault(id, List.of());
    }

    public static Map<String, List<Solution<EvolveSolution>>> loadSolutions(Map<String, OpenEvolveConfig> configs) {
        Map<String, List<Solution<EvolveSolution>>> solutions = new HashMap<>();
        configs.entrySet().stream().forEach(entry -> {
            var config = entry.getValue();
            var problem = entry.getKey();
            var solutionsJson = config.solution().path().getParent().resolve("solutions.jsonl");
            var solutionList = new CopyOnWriteArrayList<Solution<EvolveSolution>>();
            solutions.put(problem, solutionList);
            if (Files.exists(solutionsJson)) {
                try {
                    var sols = Util.readJSONL(Constants.OBJECT_MAPPER, solutionsJson, new TypeReference<Solution<EvolveSolution>>() {});
                    solutionList.addAll(sols);
                } catch (Exception e) {
                    log.warn("Failed to load solutions for problem {} from {} with error: {}", problem, solutionsJson, e.getMessage());
                }
            }
        });
        return solutions;
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
