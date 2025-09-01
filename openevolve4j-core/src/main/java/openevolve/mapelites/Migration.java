package openevolve.mapelites;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import openevolve.mapelites.Repository.Island;
import openevolve.mapelites.Repository.Solution;

public class Migration<T> {
    private final int migrationInterval;
    private final List<Integer> islandGenerations;
    private final double migrationRate;
    private final Repository<T> repository;
    private int lastMigrationGeneration = 0;

    public Migration(int migrationInterval, double migrationRate, Repository<T> repository) {
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
                if (migrant.migratedFrom() != null) {
                    continue; // only migrate originals
                }
                for (int targetIsland : targetIslands) {
                    var copy = new Solution<T>(
                            UUID.randomUUID(),
                            migrant.solution(),
                            migrant.id(),
                            migrant.fitness(),
                            iteration,
                            targetIsland,
                            migrant.cell()
                    );
                    repository.save(copy);
                }
            }
        }
        lastMigrationGeneration = maxGeneration;
    }
}
