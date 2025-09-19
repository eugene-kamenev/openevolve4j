package openevolve;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import openevolve.mapelites.Population;
import openevolve.mapelites.Population.Solution;

public class OpenEvolveFunction
		implements Function<List<Solution<EvolveSolution>>, EvolveSolution> {
	private final Population<EvolveSolution> repository;
	private final Function<EvolveStep, EvolveSolution> evolveFunction;
	private final int numberDiverseSolutions;
	private final int numberTopSolutions;

	public OpenEvolveFunction(Population<EvolveSolution> repository,
			Function<EvolveStep, EvolveSolution> evolveFunction, int numberDiverseSolutions,
			int numberTopSolutions) {
		this.repository = repository;
		this.evolveFunction = evolveFunction;
		this.numberDiverseSolutions = numberDiverseSolutions;
		this.numberTopSolutions = numberTopSolutions;
	}

	@Override
	public EvolveSolution apply(List<Solution<EvolveSolution>> t) {
		if (t == null || t.isEmpty()) {
			throw new IllegalArgumentException("Parent solutions must not be null or empty");
		}
		var parent = t.getFirst();
		var inspirations = new ArrayList<Solution<EvolveSolution>>();
		if (t.size() > 1) {
			inspirations.addAll(t.subList(1, t.size()));
		}
		return evolveFunction.apply(
				new EvolveStep(parent, inspirations, List.of(), List.of()));
	}
}
