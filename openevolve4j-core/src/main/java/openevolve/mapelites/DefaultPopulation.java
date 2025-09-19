package openevolve.mapelites;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import openevolve.mapelites.listener.Listener;
import openevolve.mapelites.listener.RepositoryListener;

public class DefaultPopulation<T> implements Population<T> {

	private final Map<UUID, Solution<T>> solutionsById = new HashMap<>();
	private final Set<UUID> archive = new HashSet<>();
	private final List<Island> islands = new ArrayList<>();
	private final List<RepositoryListener<T>> listeners = new ArrayList<>();
	private final SortedSet<Solution<T>> solutions;
	private final Comparator<Solution<T>> comparator;
	private final int populationSize;
	private final int archiveSize;
	private Island currentIsland;
	private Solution<T> bestSolution;

	public DefaultPopulation(Comparator<Solution<T>> comparator, int populationSize,
			int archiveSize, int numIslands) {
		Objects.requireNonNull(comparator, "Comparator must not be null");
		if (populationSize <= 0) {
			throw new IllegalArgumentException("Population size must be positive");
		}
		if (archiveSize <= 0) {
			throw new IllegalArgumentException("Archive size must be positive");
		}
		if (numIslands <= 0) {
			throw new IllegalArgumentException("Number of islands must be positive");
		}
		this.comparator = comparator;
		this.solutions = new TreeSet<>(this.comparator.thenComparing(Solution::id).reversed());
		this.populationSize = populationSize;
		this.archiveSize = archiveSize;
		for (int i = 0; i < numIslands; i++) {
			islands.add(new Island(i));
		}
	}

	public void addListener(RepositoryListener<T> listener) {
		if (listener != null) {
			listeners.add(listener);
		}
	}

	@Override
	public Solution<T> best() {
		return bestSolution;
	}

	@Override
	public List<Solution<T>> getArchive() {
		return archive.stream().map(solutionsById::get).filter(Objects::nonNull)
				.sorted(solutions.comparator())
				.collect(Collectors.toList());
	}

	@Override
	public Island findIslandById(int islandId) {
		return islands.get(islandId % islands.size());
	}

	@Override
	public Island nextIsland() {
		if (currentIsland == null) {
			currentIsland = islands.get(0);
		} else {
			currentIsland = islands.get((currentIsland.id() + 1) % islands.size());
		}
		return currentIsland;
	}

	@Override
	public boolean dominates(Solution<T> a, Solution<T> b) {
		if (comparator instanceof ParetoComparator<T> p) {
			return p.dominates(a, b);
		}
		return compare(a, b) > 0;
	}

	@Override
	public int compare(Solution<T> a, Solution<T> b) {
		return comparator.compare(a, b);
	}

	@Override
	public int count() {
		return solutions.size();
	}

	@Override
	public int countByIslandId(int islandId) {
		return islands.get(islandId % islands.size()).archive().size();
	}

	@Override
	public List<Solution<T>> findByIslandId(int islandId) {
		return islands.get(islandId % islands.size()).archive().stream().map(solutionsById::get)
				.filter(Objects::nonNull).sorted(solutions.comparator())
				.collect(Collectors.toList());
	}

	@Override
	public void delete(UUID id) {
		Solution<T> solution = solutionsById.remove(id);
		if (solution != null) {
			Listener.callAll(listeners, l -> l.onSolutionRemoved(solution));
			solutions.remove(solution);
		}
		for (var island : islands) {
			island.archive().remove(id);
		}
		archive.remove(id);
	}

	@Override
	public List<Solution<T>> findAll() {
		return solutions.stream().toList();
	}

	@Override
	public List<Island> findAllIslands() {
		return islands.stream().toList();
	}

	@Override
	public Solution<T> findById(UUID id) {
		return solutionsById.get(id);
	}

	@Override
	public void save(Solution<T> solution) {
		Objects.requireNonNull(solution, "solution must not be null");
		Objects.requireNonNull(solution.id(), "solution id must not be null");
		solutionsById.put(solution.id(), solution);
		solutions.add(solution);
		islands.get(solution.metadata().islandId()).archive().add(solution.id());
		Listener.callAll(listeners, l -> l.onSolutionAdded(solution));
		if (bestSolution == null || dominates(solution, bestSolution)) {
			bestSolution = solution;
		}
		afterSave(solution);
	}

	@Override
    public PopulationState snapshot() {
        var solutionsCopy = new ArrayList<>(solutionsById.keySet());
        var archiveCopy = new HashSet<UUID>(archive);
        var islandsCopy = islands.stream()
            .map(i -> (List<UUID>) new ArrayList<>(i.archive()))
            .collect(Collectors.toList());
        Integer currentId = currentIsland != null ? currentIsland.id() : null;
		return new PopulationState(solutionsCopy, archiveCopy, islandsCopy, currentId);
    }

    @Override
    public void restore(PopulationState state, Map<UUID, Solution<T>> allSolutions) {
        Objects.requireNonNull(state, "repository state must not be null");
		if (allSolutions == null || allSolutions.isEmpty()) {
			throw new IllegalArgumentException("allSolutions must not be null or empty");
		}
        // Clear all
        solutionsById.clear();
        archive.clear();
        islands.clear();
        solutions.clear();

		for (var id : state.solutions()) {
			var solution = allSolutions.get(id);
			if (solution != null) {
				solutionsById.put(id, solution);
				solutions.add(solution);
			}
		}

        // Restore solutions/sets
        archive.addAll(state.archive());

        // Rebuild islands
		int islandId = 0;
        for (var is : state.islands()) {
            var island = new Island(islandId++);
            island.archive().addAll(is);
            islands.add(island);
        }

        // Restore current island pointer
        if (state.currentIslandId() != null) {
            currentIsland = findIslandById(state.currentIslandId());
        } else {
            currentIsland = null;
        }
    }

	private void afterSave(Solution<T> saved) {
		int count = solutions.size();
		if (count >= populationSize) {
			var bestSolutionId = bestSolution.id();
			var protectedSolutions = new HashSet<UUID>();
			protectedSolutions.add(saved.id());
			protectedSolutions.add(bestSolutionId);
			var toRemove = count - populationSize;
			var allSorted = solutions.reversed(); // start from worst
			var programsToRemove = new ArrayList<Solution<T>>();

			for (var solution : allSorted) {
				if (protectedSolutions.contains(solution.id())) {
					continue;
				}
				programsToRemove.add(solution);
				if (programsToRemove.size() >= toRemove) {
					break;
				}
			}

			if (programsToRemove.size() < toRemove) {
				var remainingPrograms = allSorted.stream().filter(
						p -> !programsToRemove.contains(p) && !p.id().equals(bestSolutionId))
						.collect(Collectors.toList());
				var additionalRemovals = remainingPrograms.stream()
						.limit(toRemove - programsToRemove.size()).collect(Collectors.toList());
				programsToRemove.addAll(additionalRemovals);
			}

			for (var program : programsToRemove) {
				delete(program.id());
			}
		}
		if (archive.size() < archiveSize) {
			archive.add(saved.id());
			return;
		}

		var archivePrograms =
				solutions.stream().filter(s -> archive.contains(s.id())).toList();

		if (archivePrograms.isEmpty()) {
			return;
		}

		var worstProgram = archivePrograms.getLast();

		if (worstProgram != null && compare(saved, worstProgram) > 0) {
			archive.remove(worstProgram.id());
			archive.add(saved.id());
		}
	}
}
