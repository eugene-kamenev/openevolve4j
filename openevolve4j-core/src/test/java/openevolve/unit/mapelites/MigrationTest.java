package openevolve.unit.mapelites;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Test;
import openevolve.mapelites.DefaultPopulation;
import openevolve.mapelites.Migration;
import openevolve.mapelites.Population;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

@DisplayName("Migration Unit Tests")
public class MigrationTest {

    private Comparator<Population.Solution<String>> comparator;
    private DefaultPopulation<String> repository;

    @BeforeEach
    void setUp() {
        comparator = (a, b) -> Double.compare(
            (Double) a.fitness().get("fitness"),
            (Double) b.fitness().get("fitness")
        );
        repository = new DefaultPopulation<>(comparator, 100, 100, 3);
    }

    private Population.Solution<String> makeSolution(double fitness, int islandId) {
        Map<String, Object> fitnessMap = new HashMap<>();
        fitnessMap.put("fitness", fitness);
        return new Population.Solution<>(UUID.randomUUID(), "sol", null, fitnessMap, 0, islandId,
                new int[] { 0 });
    }

    @Test
    @DisplayName("Test migration creates copies of solutions")
    public void testMigrateCreatesCopies() {
        // create some solutions on island 0
        var s1 = makeSolution(5.0, 0);
        var s2 = makeSolution(4.0, 0);
        repository.save(s1);
        repository.save(s2);

        Migration<String> migration = new Migration<>(1, 0.5, repository);

        // call migrateSolutions on island 0
        var island = repository.findIslandById(0);
        migration.migrateSolutions(island, 1);

        // After migration, there should be additional solutions saved on neighboring islands
        int left = (island.id() - 1 + repository.findAllIslands().size()) % repository.findAllIslands().size();
        int right = (island.id() + 1) % repository.findAllIslands().size();

        boolean foundOnNeighbors = repository.findByIslandId(left).size() > 0 || repository.findByIslandId(right).size() > 0;
        assertTrue(foundOnNeighbors, "Migrants should be present on neighboring islands");
    }

    @Test
    @DisplayName("Test migration constructor validation")
    public void testConstructorValidation() {
        assertThrows(IllegalArgumentException.class, () ->
            new Migration<String>(0, 0.5, repository),
            "Should throw when interval is zero");

        assertThrows(IllegalArgumentException.class, () ->
            new Migration<String>(-1, 0.5, repository),
            "Should throw when interval is negative");

        assertThrows(IllegalArgumentException.class, () ->
            new Migration<String>(10, -0.1, repository),
            "Should throw when rate is negative");

        assertThrows(IllegalArgumentException.class, () ->
            new Migration<String>(10, 1.1, repository),
            "Should throw when rate is greater than 1");

        assertThrows(NullPointerException.class, () ->
            new Migration<String>(10, 0.5, null),
            "Should throw when repository is null");
    }

    @Test
    @DisplayName("Test migration respects rate parameter")
    public void testMigrationRate() {
        // Add many solutions to island 0
        for (int i = 0; i < 10; i++) {
            repository.save(makeSolution(i, 0));
        }

        Migration<String> migration = new Migration<>(1, 0.3, repository); // 30% migration rate

        var island = repository.findIslandById(0);
        int originalCount = repository.countByIslandId(0);
        
        migration.migrateSolutions(island, 1);

        // Should migrate approximately 30% of solutions
        int totalMigrants = 0;
        for (var otherIsland : repository.findAllIslands()) {
            if (otherIsland.id() != 0) {
                totalMigrants += repository.countByIslandId(otherIsland.id());
            }
        }

        assertTrue(totalMigrants > 0, "Should have migrated some solutions");
        assertTrue(totalMigrants <= originalCount, "Cannot migrate more than available solutions");
    }

    @Test
    @DisplayName("Test migration with empty island")
    public void testMigrationWithEmptyIsland() {
        Migration<String> migration = new Migration<>(1, 0.5, repository);
        var island = repository.findIslandById(0);

        // Should not throw exception when migrating from empty island
        assertDoesNotThrow(() -> migration.migrateSolutions(island, 1));

        // Verify no solutions were migrated
        for (var otherIsland : repository.findAllIslands()) {
            if (otherIsland.id() != 0) {
                assertEquals(0, repository.countByIslandId(otherIsland.id()));
            }
        }
    }

    @Test
    @DisplayName("Test migration to neighboring islands")
    public void testMigrationToNeighbors() {
        // Create repository with specific number of islands for predictable neighbor calculation
        repository = new DefaultPopulation<>(comparator, 100, 100, 5);
        
        var s1 = makeSolution(5.0, 2); // Island 2
        repository.save(s1);

        Migration<String> migration = new Migration<>(1, 1.0, repository); // 100% migration rate

        var island = repository.findIslandById(2);
        migration.migrateSolutions(island, 1);

        // Neighbors of island 2 should be islands 1 and 3
        int leftNeighbor = 1;
        int rightNeighbor = 3;

        boolean foundOnLeft = repository.countByIslandId(leftNeighbor) > 0;
        boolean foundOnRight = repository.countByIslandId(rightNeighbor) > 0;

        assertTrue(foundOnLeft || foundOnRight, "Should migrate to neighboring islands");
    }

    @Test
    @DisplayName("Test migration preserves solution data")
    public void testMigrationPreservesSolutionData() {
        var original = makeSolution(5.0, 0);
        repository.save(original);

        Migration<String> migration = new Migration<>(1, 1.0, repository);

        var island = repository.findIslandById(0);
        migration.migrateSolutions(island, 1);

        // Find migrated solution
        Population.Solution<String> migrated = null;
        for (var otherIsland : repository.findAllIslands()) {
            if (otherIsland.id() != 0) {
                var solutions = repository.findByIslandId(otherIsland.id());
                if (!solutions.isEmpty()) {
                    migrated = solutions.get(0);
                    break;
                }
            }
        }

        assertNotNull(migrated, "Should find migrated solution");
        assertEquals(original.solution(), migrated.solution(), "Solution data should be preserved");
        assertEquals(original.fitness(), migrated.fitness(), "Fitness should be preserved");
        assertNotEquals(original.id(), migrated.id(), "Should create new solution with different ID");
    }

    @Test
    @DisplayName("Test migration interval behavior")
    public void testMigrationInterval() {
        var s1 = makeSolution(5.0, 0);
        repository.save(s1);

        Migration<String> migration = new Migration<>(5, 1.0, repository); // Every 5 iterations

        var island = repository.findIslandById(0);

        // Should not migrate on iterations 1, 2, 3, 4
        for (int iteration = 1; iteration <= 4; iteration++) {
            int beforeCount = repository.count();
            migration.migrateSolutions(island, iteration);
            assertEquals(beforeCount, repository.count(), "Should not migrate on iteration " + iteration);
        }

        // Should migrate on iteration 5
        int beforeCount = repository.count();
        migration.migrateSolutions(island, 5);
        assertTrue(repository.count() > beforeCount, "Should migrate on iteration 5");
    }

    @Test
    @DisplayName("Test migration with single island")
    public void testMigrationWithSingleIsland() {
        repository = new DefaultPopulation<>(comparator, 100, 100, 1);
        
        var s1 = makeSolution(5.0, 0);
        repository.save(s1);

        Migration<String> migration = new Migration<>(1, 1.0, repository);

        var island = repository.findIslandById(0);
        int originalCount = repository.count();
        
        // Should not throw exception and should not change solution count
        assertDoesNotThrow(() -> migration.migrateSolutions(island, 1));
        assertEquals(originalCount, repository.count(), "Solution count should remain unchanged");
    }
}
