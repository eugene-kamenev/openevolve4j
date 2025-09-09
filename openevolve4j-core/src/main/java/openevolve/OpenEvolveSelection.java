package openevolve;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import openevolve.mapelites.Repository;
import openevolve.mapelites.Repository.Island;
import openevolve.mapelites.Repository.Solution;

public class OpenEvolveSelection
		implements Function<Island, List<Solution<EvolveSolution>>> {

	private final Repository<EvolveSolution> repository;
	private final Random random;
	private final double explorationRatio;
	private final double exploitationRatio;
	private final double eliteSelectionRatio;
	private final int numInspirations;
	private final int featureBins;

	public OpenEvolveSelection(Repository<EvolveSolution> repository, Random random,
			double explorationRatio, double exploitationRatio, double eliteSelectionRatio,
			int numInspirations, int featureBins) {
		this.repository = repository;
		this.random = random;
		this.explorationRatio = explorationRatio;
		this.exploitationRatio = exploitationRatio;
		this.eliteSelectionRatio = eliteSelectionRatio;
		this.numInspirations = numInspirations;
		this.featureBins = featureBins;
	}

	@Override
	public List<Solution<EvolveSolution>> apply(Island t) {
		var parent = sampleParent(t);
		var inspirations = sampleInspirations(parent, numInspirations);
		var combined = new ArrayList<Solution<EvolveSolution>>(inspirations.size() + 1);
		combined.add(0, parent);
		combined.addAll(inspirations);
		return combined;
	}

	protected Solution<EvolveSolution> sampleParent(Island t) {
		double randVal = random.nextDouble(); // Use the random instance from the class
		if (randVal < explorationRatio) {
			return sampleExplorationParent(t);
		} else if (randVal < explorationRatio + exploitationRatio) {
			return sampleExploitationParent(t);
		} else {
			return sampleRandomParent();
		}
	}

	protected Solution<EvolveSolution> sampleExplorationParent(Island t) {
		var currentIslandPrograms = repository.findByIslandId(t.id());
		if (currentIslandPrograms.isEmpty()) {
			return repository.best();
		}
		return currentIslandPrograms.get(random.nextInt(currentIslandPrograms.size()));
	}

	protected Solution<EvolveSolution> sampleExploitationParent(Island t) {
		var currentIsland = t.id();
		if (t.archive().isEmpty()) {
			return sampleExplorationParent(t);
		}
		var archive = repository.getArchive();
		var archiveProgramsInIsland = archive.stream().filter(Objects::nonNull)
				.filter(s -> s.islandId() == currentIsland).collect(Collectors.toList());

		if (!archiveProgramsInIsland.isEmpty()) {
			return archiveProgramsInIsland.get(random.nextInt(archiveProgramsInIsland.size()));
		}

		return archive.get(random.nextInt(archive.size()));
	}

	protected Solution<EvolveSolution> sampleRandomParent() {
		var solutions = repository.findAll();
		if (solutions.isEmpty()) {
			throw new RuntimeException("No programs available for sampling");
		}
		return solutions.get(random.nextInt(solutions.size()));
	}

	public List<Solution<EvolveSolution>> sampleInspirations(Solution<EvolveSolution> parent, int n) {
        Objects.requireNonNull(parent, "parent must not be null");
        if (n <= 0) return List.of();

        List<Solution<EvolveSolution>> inspirations = new ArrayList<>();
        var parentIsland = repository.findIslandById(parent.islandId());

        var islandSolutions = repository.findByIslandId(parentIsland.id());
        if (islandSolutions == null || islandSolutions.isEmpty()) {
            return Collections.emptyList();
        }

        // Track selected IDs to avoid duplicates and exclude parent
        var selectedIds = new HashSet<UUID>();
        selectedIds.add(parent.id());

        // Include island's best (if not the parent)
        var best = islandSolutions.get(0);
        if (!best.id().equals(parent.id())) {
            inspirations.add(best);
            selectedIds.add(best.id());
        }

        // Add top programs from the island (skip already added/parent)
        int topN = Math.max(1, (int) (n * eliteSelectionRatio));
        var topSolutions = islandSolutions.stream()
                .skip(best.id().equals(parent.id()) ? 1 : 0)
                .filter(s -> !selectedIds.contains(s.id()))
                .limit(topN)
                .toList();
        inspirations.addAll(topSolutions);
        topSolutions.forEach(s -> selectedIds.add(s.id()));

        // Add diverse programs from within the island
        var nearbySolutions = new ArrayList<Solution<EvolveSolution>>();
        if (islandSolutions.size() > n && inspirations.size() < n) {
            int remainingSlots = n - inspirations.size();

            // Create island-specific feature mapping for efficient lookup
            var islandFeatureMap = new HashMap<String, UUID>();
            for (var solution : islandSolutions) {
                islandFeatureMap.put(solution.cellId(), solution.id());
            }

            // Try to find programs from nearby feature cells within the island
            for (int attempt = 0; attempt < remainingSlots * 3 && nearbySolutions.size() < remainingSlots; attempt++) {
                var perturbedCoords = new ArrayList<Integer>();
                for (var coord : parent.cell()) {
                    int perturbed = Math.max(0, Math.min(featureBins - 1, coord + random.nextInt(5) - 2));
                    perturbedCoords.add(perturbed);
                }

                var cellKey = Solution.cellToKey(perturbedCoords.stream().mapToInt(i -> i).toArray());
                var programId = islandFeatureMap.get(cellKey);
                if (programId != null && !selectedIds.contains(programId)) {
                    var program = repository.findById(programId);
                    if (program != null) {
                        nearbySolutions.add(program);
                        selectedIds.add(program.id());
                    }
                }
            }

            // If we still need more, add random programs from the island
            if (inspirations.size() + nearbySolutions.size() < n) {
                var remaining = n - inspirations.size() - nearbySolutions.size();
                var availableIslandIds = islandSolutions.stream()
                        .map(Solution::id)
                        .filter(id -> !selectedIds.contains(id))
                        .collect(Collectors.toList());

                if (!availableIslandIds.isEmpty()) {
                    Collections.shuffle(availableIslandIds, random);
                    var randomPrograms = availableIslandIds.stream()
                            .limit(remaining)
                            .map(repository::findById)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    nearbySolutions.addAll(randomPrograms);
                    randomPrograms.forEach(p -> selectedIds.add(p.id()));
                }
            }

            inspirations.addAll(nearbySolutions);
        }
        return inspirations.subList(0, Math.min(n, inspirations.size()));
    }
}
