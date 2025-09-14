package openevolve.mapelites;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import openevolve.mapelites.listener.MAPElitesListener;
import openevolve.mapelites.listener.Listener;
import openevolve.Constants;
import openevolve.mapelites.FeatureScaler.ScaleMethod;
import openevolve.mapelites.Repository.Island;
import openevolve.mapelites.Repository.RepositoryState;
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
		LOG.trace("run called with iterations={} starting from currentIteration={}", iterations, currentIteration);
		if (!initialized) {
			Listener.callAll(listeners, listener -> listener.onAlgorithmStart(this));
			LOG.trace("Initializing archive with initial solutions generator");
			var initial = initialSolutionGenerator.get();
			LOG.trace("Generated {} initial solutions", initial.size());
			for (int i = 0; i < initial.size(); i++) {
				var island = repository.findIslandById(i);
				LOG.trace("Adding initial solution index={} to island={}", i, island != null ? island.id() : null);
				addSolution(initial.get(i), island, 0);
			}
			if (repository.count() == 0) {
				LOG.trace("No initial solutions were added to any archive - repository.count()==0");
				throw new IllegalStateException(
						"Error: No initial solutions were added to any archive");
			}
			initialized = true;
			LOG.trace("Initialization complete - repository.count()={}", repository.count());
		}

		for (; currentIteration <= iterations && !shouldStop(); currentIteration++) {
			try {
				if (Thread.currentThread().isInterrupted()) {
					LOG.warn("Thread interrupted, stopping MAP-Elites run at iteration {}", currentIteration);
					break;
				}
				var island = repository.nextIsland();
				LOG.trace("Beginning iteration {} on island {}", currentIteration, island.id());
				Listener.callAll(listeners,
						listener -> listener.onBeforeIteration(island, currentIteration, this));
				evolveIsland(island, currentIteration);
				migration.migrateSolutions(island, currentIteration);
				Listener.callAll(listeners,
						listener -> listener.onAfterIteration(island, currentIteration, this));
				LOG.trace("Finished iteration {} on island {}", currentIteration, island.id());
			} catch (Throwable t) {
				LOG.error("Error occurred during MAP-Elites iteration", t);
			}
		}
	}

	public void reset(Map<String, Cell> grid, Map<String, FeatureScaler> featureStats, int iteration) {
		LOG.trace("Resetting MAP-Elites state to iteration={} (clearing grid and featureStats)", iteration);
		this.grid.clear();
		this.grid.putAll(grid);
		this.featureStats.clear();
		this.featureStats.putAll(featureStats);
		this.currentIteration = iteration;
		this.initialized = true;
		LOG.trace("Reset complete: grid.size={} featureStats.size={} currentIteration={}",
				this.grid.size(), this.featureStats.size(), this.currentIteration);
	}

	public Map<String, Cell> getGrid() {
		return Collections.unmodifiableMap(grid);
	}

	public Map<String, FeatureScaler> getFeatureStats() {
		return Collections.unmodifiableMap(featureStats);
	}

	public RepositoryState getRepositoryState() {
		return repository.snapshot();
	}

	public int[] calculateFeatureCoords(T evolved, Map<String, Object> fitness) {
		LOG.trace("Calculating feature coordinates for evolved candidate");
		int[] coords = new int[featureDimensions.size()];
		for (int i = 0; i < featureDimensions.size(); i++) {
			String dim = featureDimensions.get(i);
			double featureValue = getFeatureValue(dim, evolved, fitness);
			int binIndex = calculateBinIndex(dim, featureValue, featureBins);
			coords[i] = binIndex;
		}
		LOG.trace("Calculated feature coords: {}", Arrays.toString(coords));
		return coords;
	}

	public boolean addToGrid(Solution<T> newSolution) {
		var coords = newSolution.cellId();
		LOG.trace("Attempting to add solution id={} to cell={}", newSolution.id(), coords);
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
			LOG.trace("Cell {} improved by solution id={}", coords, newSolution.id());
			Listener.callAll(listeners, listener -> listener.onCellImproved(newSolution, current, newCell,
					newSolution.iteration()));
			return true;
		} else if (cell != null) {
			var updatedCell = new Cell(cell.key(), cell.trials() + 1, cell.curiosity(),
					cell.improveIter(), cell.solutionId());
			grid.put(coords, updatedCell);
			LOG.trace("Cell {} rejected new solution id={} (current solution id={})", coords, newSolution.id(), currentId);
			Listener.callAll(listeners, listener -> listener.onCellRejected(newSolution, current, updatedCell,
					newSolution.iteration()));
		} else {
			LOG.trace("No cell existed for coords {} and solution id={} did not improve", coords, newSolution.id());
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
			LOG.trace("Dimension {} uses diversity but repository count < 2, returning bin 0", dim);
			return 0;
		}
		double scaledValue = scaleFeature(dim, featureValue);
		int numBins = featureBinsPerDim.getOrDefault(dim, defaultBins);
		int rawIdx = (int) (scaledValue * numBins);
		int clamped = Math.max(0, Math.min(numBins - 1, rawIdx));
		LOG.trace("calculateBinIndex dim={} value={} scaled={} bins={} rawIdx={} clampedIdx={}",
				dim, featureValue, scaledValue, numBins, rawIdx, clamped);
		return clamped;
	}

	protected double scaleFeature(String feature, double value) {
		FeatureScaler scaler =
				featureStats.computeIfAbsent(feature, _ -> new FeatureScaler(featureScaleMethod)).apply(value);
		featureStats.put(feature, scaler);
		double scaled = scaler.scaled(value);
		LOG.trace("scaleFeature feature={} rawValue={} scaledValue={}", feature, value, scaled);
		return scaled;
	}

	private boolean shouldStop() {
		boolean stop = stopCondition.test(repository.best());
		LOG.trace("shouldStop evaluated to {} (bestId={})", stop,
				repository.best() != null ? repository.best().id() : null);
		return stop;
	}

	private void evolveIsland(Island island, int iteration) {
		var selected = selection.apply(island);
		LOG.trace("evolveIsland island={} iteration={} selectedCandidates={}", island.id(), iteration, selected.size());
		if (selected.isEmpty()) {
			LOG.warn("No solutions selected for evolution on island {} at iteration {}",
					island.id(), iteration);
			return;
		}
		Listener.callAll(listeners, listener -> listener.onSolutionSelection(selected, island, iteration));
		var evolved = evolveOperator.apply(selected);
		LOG.trace("Evolution operator produced: {}", evolved);
		if (evolved == null) {
			LOG.trace("Evolved result is null, skipping addSolution");
			return;
		}
		var solution = addSolution(evolved, island, iteration);
		LOG.trace("Solution added with id={} on island={}", solution.id(), island.id());
		Listener.callAll(listeners, listener -> listener.onSolutionGenerated(solution, selected, iteration));
	}

	private Solution<T> addSolution(T evolved, Island island, int iteration) {
		LOG.trace("addSolution called for island={} iteration={}", island.id(), iteration);
		var fitness = fitnessFunction.apply(evolved);
		LOG.trace("Computed fitness for evolved candidate: {}", fitness);
		var coords = calculateFeatureCoords(evolved, fitness);
		var solution = new Solution<T>(UUID.randomUUID(), evolved, null, fitness, iteration, island.id(), coords);
		LOG.trace("Constructed Solution id={} coords={}", solution.id(), Arrays.toString(coords));
		var bestBefore = repository.best();
		boolean improved = addToGrid(solution);
		LOG.trace("addToGrid returned improved={} for solution id={}", improved, solution.id());
		if (bestBefore == null || repository.best().id().equals(solution.id())) {
			// best solution is the same solution now
			Listener.callAll(listeners, listener -> listener.onNewBestSolution(solution, bestBefore, iteration));
			LOG.trace("New best solution set id={} (previous best was {})", solution.id(), bestBefore != null ? bestBefore.id() : null);
		}
		return solution;
	}

	public record Cell(String key, int trials, double curiosity, int improveIter, UUID solutionId) {
	}

	private static void trace(String format, Object... args) {
		if (LOG.isTraceEnabled()) {
			LOG.trace(String.format(format, args));
		}
	}
}
