package openevolve.domain;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

public interface FitnessAware extends Serializable {
	UUID id();
	UUID parentId();
	Map<String, Object> fitness();

	public interface Data extends Serializable {}
}
