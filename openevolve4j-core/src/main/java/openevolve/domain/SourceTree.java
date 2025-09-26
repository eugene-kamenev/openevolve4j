package openevolve.domain;

import java.nio.file.Path;
import java.util.Map;

public record SourceTree(Map<Path, String> files, Map<String, Object> metadata)
		implements FitnessAware.Data {

	public boolean fullRewrite() {
		return (Boolean) metadata.getOrDefault("fullRewrite", false);
	}
}
