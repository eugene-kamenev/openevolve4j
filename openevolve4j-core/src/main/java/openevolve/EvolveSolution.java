package openevolve;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EvolveSolution(
		UUID parentId,
		Instant dateCreated,
		Map<Path, String> files,
		String language,
		String changes,
		Map<String, Object> parentMetrics,
		boolean fullRewrite) {

	@Override
	public String toString() {
		return "EvolveSolution []"; // nothing useful we can use from the properties
	}
			
}
