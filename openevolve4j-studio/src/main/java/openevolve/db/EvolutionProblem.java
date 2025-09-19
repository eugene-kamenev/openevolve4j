package openevolve.db;

import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import openevolve.OpenEvolveConfig;

@Table("evolution_problem")
public record EvolutionProblem(@Id UUID id, String name, OpenEvolveConfig config) {
	public EvolutionProblem {
		if (id == null) {
			id = UUID.randomUUID();
		}
	}
}
