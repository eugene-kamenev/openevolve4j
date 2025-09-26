package openevolve.domain;

import java.util.List;

public record EvolutionStep<S extends FitnessAware>(
		S parent,
		List<S> children,
		List<S> ancestors) {
}
