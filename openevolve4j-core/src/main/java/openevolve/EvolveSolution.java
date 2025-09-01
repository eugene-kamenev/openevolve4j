package openevolve;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

public record EvolveSolution(
		UUID parentId,
		Path parentPath,
		Path path,
		String content,
		String language,
		String changes,
		Map<String, Object> parentMetrics,
		boolean fullRewrite) {

	@Override
	public String toString() {
		return "EvolveSolution []"; // nothing useful we can use from the properties
	}
			
}
