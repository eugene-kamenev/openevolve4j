package openevolve.db;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import com.fasterxml.jackson.annotation.JsonFormat;
import openevolve.EvolveSolution;
import openevolve.StudioConstants;
import openevolve.mapelites.Population.MAPElitesMetadata;
import openevolve.mapelites.Population.Solution;

@Table("solution")
public record EvolutionSolution(@Id UUID id, UUID parentId, UUID problemId, UUID runId, @JsonFormat(pattern = StudioConstants.DATE_FORMAT, timezone = "UTC") @CreatedDate Instant dateCreated,
		EvolveSolution solution, Map<String, Object> fitness, MAPElitesMetadata metadata) {

	public Solution<EvolveSolution> toPopulationSolution() {
		return new Solution<>(id, parentId, runId, dateCreated, solution, fitness, metadata);
	}

	public static EvolutionSolution fromPopulationSolution(Solution<EvolveSolution> sol, UUID evolutionProblemId) {
		return new EvolutionSolution(sol.id(), sol.parentId(), evolutionProblemId, sol.runId(), sol.dateCreated(),
				sol.solution(), sol.fitness(), sol.metadata());
	}
}
