package openevolve.mapelites;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import openevolve.mapelites.listener.MAPElitesListener;
import openevolve.Constants;
import openevolve.mapelites.FeatureScaler.ScaleMethod;
import openevolve.mapelites.Repository.Island;
import openevolve.mapelites.Repository.Solution;

/**
 * Multi-dimensional Archive of Phenotypic Elites with Islands
 */
public class MAPElites<T> {

	private static final Logger LOG = LoggerFactory.getLogger(MAPElites.class);

	private final Function<T, Map<String, Object>> fitnessFunction;
	private final Function<List<Solution<T>>, T> evolveOperator;
	private final Function<Island, List<Solution<T>>> selection;
	private final Supplier<List<T>> initialSolutionGenerator;
	private final Predicate<Solution<T>> stopCondition;

	private final Map<String, Cell> grid = new HashMap<>();
	private final Map<String, FeatureScaler> featureStats = new HashMap<>();
	private final Map<String, Integer> featureBinsPerDim = new HashMap<>();
	private final List<String> featureDimensions;
	private final int featureBins;
	private final ScaleMethod featureScaleMethod;

	private final Repository<T> repository;
	private final Migration<T> migration;
	private final List<MAPElitesListener<T>> listeners = new ArrayList<>();
	private int currentIteration = 1;
	private boolean initialized = false;

	public MAPElites(Repository<T> repository, Migration<T> migration,
			Function<T, Map<String, Object>> fitnessFunction,
			Function<List<Solution<T>>, T> evolveOperator,
			Supplier<List<T>> initialSolutionGenerator,
			Function<Island, List<Solution<T>>> selection,
			Predicate<Solution<T>> stopCondition,
			ScaleMethod featureScaleMethod,
			List<String> featureDimensions,
			int defaultFeatureBins) {
		Objects.requireNonNull(featureDimensions, "Feature dimensions must not be null");
		if (featureDimensions.isEmpty()) {
			throw new IllegalArgumentException("Feature dimensions must not be empty");
		}
		if (defaultFeatureBins <= 0) {
			throw new IllegalArgumentException("Default feature bins must be positive");
		}
		this.featureDimensions = featureDimensions;
		this.featureBins = defaultFeatureBins;
		this.fitnessFunction = fitnessFunction;
		this.evolveOperator = evolveOperator;
		this.initialSolutionGenerator = initialSolutionGenerator;
		this.repository = repository;
		this.selection = selection;
		this.migration = migration;
		this.stopCondition = stopCondition;
		this.featureScaleMethod = featureScaleMethod;
	}

	public void setIteration(int iteration) {
		this.currentIteration = iteration;
	}

	public void printArchive() {
		// group by island
		Map<Integer, List<Solution<T>>> groupedByIsland =
				repository.getArchive().stream().collect(Collectors.groupingBy(Solution::islandId));
		for (var group : groupedByIsland.entrySet()) {
			int islandId = group.getKey();
			List<Solution<T>> solutions = group.getValue();
			System.out.println("=== Island " + islandId + " (size: " + solutions.size() + ") ===");
			for (Solution<T> solution : solutions) {
				var coords = solution.cellId();
				System.out.println("Cell " + coords + " -> " + solution.solution() + " (fitness="
						+ solution.fitness() + ")");
			}
		}
	}

	public void addListener(MAPElitesListener<T> listener) {
		listeners.add(listener);
	}

	public void run(int iterations) {
		if (!initialized) {
			currentIteration = 1;
			callListeners(listener -> listener.onAlgorithmStart(this));
			var initial = initialSolutionGenerator.get();
			for (int i = 0; i < initial.size(); i++) {
				addSolution(initial.get(i), repository.findIslandById(i), 0);
			}
			if (repository.count() == 0) {
				throw new IllegalStateException(
						"Error: No initial solutions were added to any archive");
			}
			initialized = true;
		}

		for (; currentIteration <= iterations && !shouldStop(); currentIteration++) {
			try {
				var island = repository.nextIsland();
				callListeners(
						listener -> listener.onBeforeIteration(island, currentIteration, this));
				evolveIsland(island, currentIteration);
				migration.migrateSolutions(island, currentIteration);
				callListeners(
						listener -> listener.onAfterIteration(island, currentIteration, this));
			} catch (Throwable t) {
				LOG.error("Error occurred during MAP-Elites iteration", t);
			}
		}
	}

	public Snapshot<T> snapshot() {
		return new Snapshot<>(currentIteration, repository.snapshot(), new HashMap<>(grid), new HashMap<>(featureStats));
	}

	public int[] calculateFeatureCoords(T evolved, Map<String, Object> fitness) {
		int[] coords = new int[featureDimensions.size()];
		for (int i = 0; i < featureDimensions.size(); i++) {
			String dim = featureDimensions.get(i);
			double featureValue = getFeatureValue(dim, evolved, fitness);
			int binIndex = calculateBinIndex(dim, featureValue, featureBins);
			coords[i] = binIndex;
		}
		return coords;
	}

	public boolean addToGrid(Solution<T> newSolution) {
		var coords = newSolution.cellId();
		var cell = grid.get(coords);
		var currentId = cell != null ? cell.solutionId() : null;
		var current = currentId != null ? repository.findById(currentId) : null;

		boolean isImprovement = current == null || repository.dominates(newSolution, current);

		repository.save(newSolution);
		if (isImprovement) {
			var newCell = new Cell(coords, cell != null ? cell.trials() + 1 : 1,
					cell != null ? cell.curiosity() : 0.0, newSolution.iteration(),
					newSolution.id());
			grid.put(coords, newCell);
			callListeners(listener -> listener.onCellImproved(newSolution, current, newCell,
					newSolution.iteration()));
			return true;
		} else if (cell != null) {
			var updatedCell = new Cell(cell.key(), cell.trials() + 1, cell.curiosity(),
					cell.improveIter(), cell.solutionId());
			grid.put(coords, updatedCell);
			callListeners(listener -> listener.onCellRejected(newSolution, current, updatedCell,
					newSolution.iteration()));
		}
		return false;
	}

	protected double getFeatureValue(String feature, T evolved, Map<String, Object> fitness) {
		Object value = fitness.get(feature);
		if (value instanceof Number) {
			return ((Number) value).doubleValue();
		}
		return Double.NaN; // NaN will give zero index
	}

	protected int calculateBinIndex(String dim, double featureValue, int defaultBins) {
		if (Constants.DIVERSITY.equals(dim) && repository.count() < 2) {
			return 0;
		}
		double scaledValue = scaleFeature(dim, featureValue);
		int numBins = featureBinsPerDim.getOrDefault(dim, defaultBins);
		int idx = (int) (scaledValue * numBins);
		return Math.max(0, Math.min(numBins - 1, idx));
	}

	protected double scaleFeature(String feature, double value) {
		FeatureScaler scaler =
				featureStats.computeIfAbsent(feature, _ -> new FeatureScaler(featureScaleMethod)).apply(value);
		featureStats.put(feature, scaler);
		return scaler.scaled(value);
	}

	private boolean shouldStop() {
		var best = repository.findAll().getFirst();
		return stopCondition.test(best);
	}

	private void evolveIsland(Island island, int iteration) {
		var selected = selection.apply(island);
		if (selected.isEmpty()) {
			LOG.warn("No solutions selected for evolution on island {} at iteration {}",
					island.id(), iteration);
			return;
		}
		callListeners(listener -> listener.onSolutionSelection(selected, island, iteration));
		var evolved = evolveOperator.apply(selected);
		if (evolved == null) {
			return;
		}
		var solution = addSolution(evolved, island, iteration);
		callListeners(listener -> listener.onSolutionGenerated(solution, selected, iteration));
	}

	private Solution<T> addSolution(T evolved, Island island, int iteration) {
		var fitness = fitnessFunction.apply(evolved);
		var coords = calculateFeatureCoords(evolved, fitness);
		var solution = new Solution<T>(UUID.randomUUID(), evolved, null, fitness, iteration, island.id(), coords);
		var bestBefore = repository.best();
		addToGrid(solution);
		if (bestBefore == null || repository.dominates(solution, bestBefore)) {
			// best solution is the same solution now
			callListeners(listener -> listener.onNewBestSolution(solution,
					bestBefore, iteration));
		}
		return solution;
	}

	private void callListeners(Consumer<MAPElitesListener<T>> action) {
		listeners.forEach(listener -> {
			try {
				action.accept(listener);
			} catch (Exception e) {
				LOG.error("Error occurred while calling listener", e);
			}
		});
	}

	public record Cell(String key, int trials, double curiosity, int improveIter, UUID solutionId) {
	}

	public record Snapshot<T>(int iteration, Repository.RepositoryState<T> repository, Map<String, Cell> grid, Map<String, FeatureScaler> featureStats) {

	}
}
