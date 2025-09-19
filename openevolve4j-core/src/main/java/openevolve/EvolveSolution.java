package openevolve;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import openevolve.BaseAgent.Change;

public record EvolveSolution(
		Map<Path, String> files,
		Map<Path, List<Change>> changes,
		Map<String, Object> parentMetrics,
		Map<String, Object> metadata) {

	@Override
	public String toString() {
		return "EvolveSolution []"; // nothing useful we can use from the properties
	}
			
	public boolean fullRewrite() {
		return (Boolean) metadata.getOrDefault("fullRewrite", false);
	}
}
