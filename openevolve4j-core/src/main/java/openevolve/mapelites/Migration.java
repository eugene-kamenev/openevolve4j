package openevolve.mapelites;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import openevolve.mapelites.Population.Island;
import openevolve.mapelites.Population.Solution;
import openevolve.mapelites.Population.MAPElitesMetadata;;

public class Migration<T> {
    private final int migrationInterval;
    private final List<Integer> islandGenerations;
    private final double migrationRate;
    private final Population<T> repository;
    private int lastMigrationGeneration = 0;

    public Migration(int migrationInterval, double migrationRate, Population<T> repository) {
        Objects.requireNonNull(repository, "Repository must not be null");
        if (migrationInterval <= 0) {
            throw new IllegalArgumentException("Migration interval must be positive");
        }
        if (migrationRate < 0 || migrationRate > 1) {
            throw new IllegalArgumentException("Migration rate must be between 0 and 1");
        }
        this.migrationInterval = migrationInterval;
        this.migrationRate = migrationRate;
        this.repository = repository;
        this.islandGenerations = repository.findAllIslands().stream()
                .map(_ -> 0).collect(Collectors.toList());
    }

    public void migrateSolutions(Island currentIsland, int iteration) {
        islandGenerations.set(currentIsland.id(), islandGenerations.get(currentIsland.id()) + 1);
        var maxGeneration = islandGenerations.stream().max(Integer::compareTo).orElse(0);
        if ((maxGeneration - lastMigrationGeneration) < migrationInterval) {
            return;
        }
        var islands = repository.findAllIslands();
        if (islands.size() < 2) {
            lastMigrationGeneration = maxGeneration;
            return;
        }

        for (int i = 0; i < islands.size(); i++) {
            var islandPrograms = islands.get(i)
                    .archive().stream()
                    .map(repository::findById)
                    .filter(Objects::nonNull)
                    .sorted((a, b) -> -repository.compare(a, b)) // best first
                    .collect(Collectors.toList());

            if (islandPrograms.isEmpty()) {
                continue;
            }

            int numToMigrate = Math.max(1, (int) Math.ceil(islandPrograms.size() * this.migrationRate));
            var migrants = islandPrograms.subList(0, Math.min(numToMigrate, islandPrograms.size()));

            int left = (i - 1 + islands.size()) % islands.size();
            int right = (i + 1) % islands.size();

            // Dedup targets if islands.size()==2 (left == right)
            int[] targetIslands = (left == right) ? new int[] { left } : new int[] { left, right };

            for (var migrant : migrants) {
                if (migrant.metadata().migratedFrom() != null) {
                    continue; // only migrate originals
                }
                for (int targetIsland : targetIslands) {
                    var metadata = new MAPElitesMetadata(migrant.id(), iteration, targetIsland, migrant.metadata().cell());
                    var copy = new Solution<T>(
                            UUID.randomUUID(),
                            migrant.parentId(),
                            migrant.runId(),
                            migrant.dateCreated(),
                            migrant.solution(),
                            migrant.fitness(),
                            metadata
                    );
                    repository.save(copy);
                }
            }
        }
        lastMigrationGeneration = maxGeneration;
    }
}
