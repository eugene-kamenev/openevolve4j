package openevolve;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import openevolve.BaseAgent.Change;
import openevolve.util.PathKeyDeserializer;

public record EvolveSolution(
		UUID parentId,
		Instant dateCreated,
		@JsonDeserialize(keyUsing = PathKeyDeserializer.class)
		Map<Path, String> files,
		@JsonDeserialize(keyUsing = PathKeyDeserializer.class)
		Map<Path, List<Change>> changes,
		Map<String, Object> parentMetrics,
		Map<String, Object> metadata,
		boolean fullRewrite) {

	@Override
	public String toString() {
		return "EvolveSolution []"; // nothing useful we can use from the properties
	}
			
}
