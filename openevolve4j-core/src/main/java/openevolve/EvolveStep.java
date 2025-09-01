package openevolve;

import java.util.List;
import openevolve.mapelites.Repository.Solution;

public record EvolveStep(Solution<EvolveSolution> parent,
		List<Solution<EvolveSolution>> inspirations,
		List<Solution<EvolveSolution>> previousSolutions,
		List<Solution<EvolveSolution>> topSolutions) {
}
