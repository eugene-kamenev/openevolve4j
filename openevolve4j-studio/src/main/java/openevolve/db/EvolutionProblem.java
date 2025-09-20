package openevolve.db;

import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import openevolve.api.AlgorithmConfig;

@Table("evolution_problem")
public record EvolutionProblem<T extends AlgorithmConfig>(@Id UUID id, String name, T config) {
	public EvolutionProblem {
		if (id == null) {
			id = UUID.randomUUID();
		}
	}
}
