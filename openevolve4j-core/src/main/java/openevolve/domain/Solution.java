package openevolve.domain;

import java.util.Map;
import java.util.UUID;

public record Solution<T extends FitnessAware.Data>(UUID id, UUID parentId,
		Map<String, Object> fitness, T data) implements FitnessAware {
}
