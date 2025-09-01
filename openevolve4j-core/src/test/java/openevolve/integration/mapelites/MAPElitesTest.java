package openevolve.integration.mapelites;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import openevolve.mapelites.listener.MAPElitesListener;
import openevolve.mapelites.DefaultRepository;
import openevolve.mapelites.MAPElites;
import openevolve.mapelites.Migration;
import openevolve.mapelites.Repository;
import openevolve.mapelites.FeatureScaler.ScaleMethod;
import openevolve.mapelites.Repository.Solution;

@DisplayName("MAPElites Integration Tests")
public class MAPElitesTest {

    private Comparator<Repository.Solution<String>> comparator;
    private DefaultRepository<String> repository;
    private Migration<String> migration;

    @BeforeEach
    void setUp() {
        comparator = (a, b) -> Double.compare(
            (Double) a.fitness().get("fitness"),
            (Double) b.fitness().get("fitness")
        );
        repository = new DefaultRepository<>(comparator, 100, 100, 2);
        migration = new Migration<>(100, 0.1, repository);
    }

    @Test
    @DisplayName("Test MAPElites run initializes and notifies listeners")
    public void testRunInitializesAndNotifiesListeners() {
        // simple fitness: identity stored under "fitness"
        Function<String, Map<String, Object>> fitnessFn = s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("fitness", Double.parseDouble(s));
            return m;
        };

        // evolve operator: pick first parent's solution (string) and return as new
        java.util.function.Function<List<Repository.Solution<String>>, String> evolve =
            parents -> parents.get(0).solution();

        // selection: pick any from island archive
        java.util.function.Function<Repository.Island, List<Repository.Solution<String>>> selection =
            island -> repository.findByIslandId(island.id());

        // initial solution generator: produce two simple string-numbered solutions
        java.util.function.Supplier<List<String>> initialGen = () -> List.of("1.0", "2.0");

        MAPElites<String> map = new MAPElites<>(repository, migration, fitnessFn, evolve, initialGen, selection, _ -> false, ScaleMethod.MIN_MAX, List.of("fitness"), 5);

        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean afterIter = new AtomicBoolean(false);

        map.addListener(new MAPElitesListener<String>() {
            @Override
            public void onAlgorithmStart(MAPElites<String> mapElites) {
                started.set(true);
            }

            @Override
            public void onAfterIteration(Repository.Island island, int iteration, MAPElites<String> mapElites) {
                afterIter.set(true);
            }
        });

        map.run(2);

        assertTrue(started.get(), "onAlgorithmStart should be invoked");
        assertTrue(afterIter.get(), "onAfterIteration should be invoked at least once");

        // repository should have initial solutions added
        assertTrue(repository.count() > 0, "Repository should contain initial solutions");
    }

    @Test
    @DisplayName("Test MAPElites with complex fitness landscape")
    public void testComplexFitnessLandscape() {
        // Create new repository for Double type
        Repository<Double> doubleRepo = new DefaultRepository<>(
            (a, b) -> Double.compare((Double) a.fitness().get("fitness"), (Double) b.fitness().get("fitness")),
            100, 50, 3);
        Migration<Double> doubleMigration = new Migration<>(50, 0.1, doubleRepo);

        // Multi-modal fitness function: fitness peaks at integers
        Function<Double, Map<String, Object>> fitnessFn = value -> {
            Map<String, Object> m = new HashMap<>();
            double nearestInt = Math.round(value);
            double distance = Math.abs(value - nearestInt);
            double fitness = Math.exp(-10 * distance); // Peaks at integers
            m.put("fitness", fitness);
            return m;
        };

        // Evolution operator: mutation around current value
        java.util.function.Function<List<Solution<Double>>, Double> evolve = parents -> {
            if (parents.isEmpty()) return Math.random() * 10;
            Double parent = parents.get(0).solution();
            return parent + (Math.random() - 0.5) * 0.5; // Small mutation
        };

        // Selection: tournament selection
        java.util.function.Function<Repository.Island, List<Solution<Double>>> selection = island -> {
            var solutions = doubleRepo.findByIslandId(island.id());
            if (solutions.size() <= 2) return solutions;
            
            // Simple tournament: pick 2 best
            Collections.sort(solutions, (a, b) -> Double.compare(
                (Double) b.fitness().get("fitness"),
                (Double) a.fitness().get("fitness")
            ));
            return solutions.subList(0, 2);
        };

        // Initial solutions: random values
        java.util.function.Supplier<List<Double>> initialGen = () -> {
            List<Double> initial = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                initial.add(Math.random() * 10);
            }
            return initial;
        };

        MAPElites<Double> mapElites = new MAPElites<>(
            doubleRepo, doubleMigration, fitnessFn, evolve, initialGen, selection, _ -> false, ScaleMethod.MIN_MAX, List.of("fitness"), 10);

        mapElites.run(100);

        // Verify algorithm found good solutions
        var allSolutions = doubleRepo.findAll();
        assertFalse(allSolutions.isEmpty(), "Should find solutions");
        
        double bestFitness = allSolutions.stream()
            .mapToDouble(sol -> (Double) sol.fitness().get("fitness"))
            .max()
            .orElse(0.0);
        
        assertTrue(bestFitness > 0.5, "Should find reasonably good solutions, got: " + bestFitness);
    }

    @Test
    @DisplayName("Test MAPElites with no initial solutions")
    public void testWithNoInitialSolutions() {
        Function<String, Map<String, Object>> fitnessFn = s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("fitness", 1.0);
            return m;
        };

        java.util.function.Function<List<Solution<String>>, String> evolve =
            _ -> "evolved";

        java.util.function.Function<Repository.Island, List<Solution<String>>> selection =
            island -> repository.findByIslandId(island.id());

        // Empty initial generation
        java.util.function.Supplier<List<String>> initialGen = () -> List.of();

        MAPElites<String> mapElites = new MAPElites<>(
            repository, migration, fitnessFn, evolve, initialGen, selection, _ -> false, ScaleMethod.MIN_MAX, List.of("fitness"), 5);

        // Should handle empty initialization gracefully
        assertThrows(IllegalStateException.class, () -> mapElites.run(5));
    }

    @Test
    @DisplayName("Test MAPElites listener management")
    public void testListenerManagement() {
        Function<String, Map<String, Object>> fitnessFn = s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("fitness", 1.0);
            return m;
        };

        java.util.function.Function<List<Solution<String>>, String> evolve =
            _ -> "evolved";

        java.util.function.Function<Repository.Island, List<Solution<String>>> selection =
            _ -> List.of();

        java.util.function.Supplier<List<String>> initialGen = () -> List.of("initial");

        MAPElites<String> mapElites = new MAPElites<>(
            repository, migration, fitnessFn, evolve, initialGen, selection, _ -> false, ScaleMethod.MIN_MAX, List.of("fitness"), 5);

        AtomicBoolean listener1Called = new AtomicBoolean(false);
        AtomicBoolean listener2Called = new AtomicBoolean(false);

        MAPElitesListener<String> listener1 = new MAPElitesListener<String>() {
            @Override
            public void onAlgorithmStart(MAPElites<String> mapElites) {
                listener1Called.set(true);
            }
            @Override
            public void onAfterIteration(Repository.Island island, int iteration, MAPElites<String> mapElites) {}
        };

        MAPElitesListener<String> listener2 = new MAPElitesListener<String>() {
            @Override
            public void onAlgorithmStart(MAPElites<String> mapElites) {
                listener2Called.set(true);
            }
            @Override
            public void onAfterIteration(Repository.Island island, int iteration, MAPElites<String> mapElites) {}
        };

        mapElites.addListener(listener1);
        mapElites.addListener(listener2);

        mapElites.run(1);

        assertTrue(listener1Called.get(), "First listener should be called");
        assertTrue(listener2Called.get(), "Second listener should be called");
    }

    @Test
    @DisplayName("Test MAPElites with custom migration interval")
    public void testCustomMigrationInterval() {
        Migration<String> customMigration = new Migration<>(3, 0.5, repository); // Every 3 iterations

        Function<String, Map<String, Object>> fitnessFn = s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("fitness", Double.parseDouble(s));
            return m;
        };

        java.util.function.Function<List<Solution<String>>, String> evolve = parents -> {
            if (parents.isEmpty()) return "1.0";
            return String.valueOf(Double.parseDouble(parents.get(0).solution()) + 0.1);
        };

        java.util.function.Function<Repository.Island, List<Solution<String>>> selection =
            island -> repository.findByIslandId(island.id());

        java.util.function.Supplier<List<String>> initialGen = () -> List.of("1.0", "2.0");

        MAPElites<String> mapElites = new MAPElites<>(
            repository, customMigration, fitnessFn, evolve, initialGen, selection, _ -> false, ScaleMethod.MIN_MAX, List.of("fitness"), 5);

        int initialCount = repository.count();
        mapElites.run(6); // Should trigger migration at iterations 3 and 6

        // Should have more solutions due to migration
        assertTrue(repository.count() >= initialCount, "Migration should add solutions");
    }

    @Test
    @DisplayName("Test MAPElites iteration control")
    public void testIterationControl() {
        Function<String, Map<String, Object>> fitnessFn = s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("fitness", 1.0);
            return m;
        };

        java.util.function.Function<List<Solution<String>>, String> evolve =
            _ -> "evolved";

        java.util.function.Function<Repository.Island, List<Solution<String>>> selection =
            _ -> List.of();

        java.util.function.Supplier<List<String>> initialGen = () -> List.of("initial");

        MAPElites<String> mapElites = new MAPElites<>(
            repository, migration, fitnessFn, evolve, initialGen, selection, _ -> false, ScaleMethod.MIN_MAX, List.of("fitness"), 5);

        final int[] maxIteration = {0};
        mapElites.addListener(new MAPElitesListener<String>() {
            @Override
            public void onAlgorithmStart(MAPElites<String> mapElites) {}
            
            @Override
            public void onAfterIteration(Repository.Island island, int iteration, MAPElites<String> mapElites) {
                maxIteration[0] = Math.max(maxIteration[0], iteration);
            }
        });

        mapElites.run(10);

        assertEquals(10, maxIteration[0], "Should run for exactly 10 iterations");
    }

    @Test
    @DisplayName("Test MAPElites with exception in operators")
    public void testExceptionHandling() {
        java.util.function.Function<String, Map<String, Object>> fitnessFn = s -> {
            if ("error".equals(s)) {
                throw new RuntimeException("Fitness evaluation error");
            }
            Map<String, Object> m = new HashMap<>();
            m.put("fitness", 1.0);
            return m;
        };

        java.util.function.Function<List<Solution<String>>, String> evolve = parents -> {
            if (parents.isEmpty()) return "normal";
            return "error"; // Will cause fitness function to throw
        };

        java.util.function.Function<Repository.Island, List<Solution<String>>> selection =
            island -> repository.findByIslandId(island.id());

        java.util.function.Supplier<List<String>> initialGen = () -> List.of("normal");

        MAPElites<String> mapElites = new MAPElites<>(
            repository, migration, fitnessFn, evolve, initialGen, selection, _ -> false, ScaleMethod.MIN_MAX, List.of("fitness"), 5);

        // Should handle exceptions gracefully without crashing
        assertDoesNotThrow(() -> mapElites.run(5));
    }

    @Test
    @DisplayName("Test MAPElites print archive functionality")
    public void testPrintArchive() {
        java.util.function.Function<String, Map<String, Object>> fitnessFn = s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("fitness", Double.parseDouble(s));
            return m;
        };

        java.util.function.Function<List<Solution<String>>, String> evolve =
            _ -> "1.0";

        java.util.function.Function<Repository.Island, List<Solution<String>>> selection =
            _ -> List.of();

        java.util.function.Supplier<List<String>> initialGen = () -> List.of("1.0", "2.0");

        MAPElites<String> mapElites = new MAPElites<>(
            repository, migration, fitnessFn, evolve, initialGen, selection, _ -> false, ScaleMethod.MIN_MAX, List.of("fitness"), 5);

        mapElites.run(2);

        // Should not throw exception when printing archive
        assertDoesNotThrow(() -> mapElites.printArchive());
    }
}
