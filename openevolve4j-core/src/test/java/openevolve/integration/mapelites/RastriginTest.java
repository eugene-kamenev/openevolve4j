package openevolve.integration.mapelites;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import openevolve.Constants;
import openevolve.mapelites.listener.MAPElitesLoggingListener;
import openevolve.mapelites.DefaultPopulation;
import openevolve.mapelites.MAPElites;
import openevolve.mapelites.Migration;
import openevolve.mapelites.Population;
import openevolve.mapelites.FeatureScaler.ScaleMethod;
import openevolve.mapelites.Population.Island;
import openevolve.mapelites.Population.Solution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

/**
 * Single-objective MAP-Elites integration test on the Rastrigin domain (minimization).
 * Domain: x_i in [-5.12, 5.12], i = 1..N. Global optimum at x = 0^N with f(x) = 0.
 */
@DisplayName("Rastrigin Problem Integration Tests")
public class RastriginTest {

    private static final int DIM = 10;
    private static final double LOWER = -5.12;
    private static final double UPPER = 5.12;
    private static final int POPULATION_SIZE = 200;
    private static final int ARCHIVE_SIZE = 120;
    private static final int NUM_ISLANDS = 4;
    private static final int NUM_INSPIRATIONS = 3;
    private static final int ITERATIONS = 500;
    private static final int MIGRATION_INTERVAL = 75;
    private static final double MUTATION_RATE = 0.25;
    private static final double MUTATION_SCALE = 0.5; // step size stddev
    private static final double CROSSOVER_RATE = 0.5;
    private Random random;

    @BeforeEach
    void setUp() {
        random = new Random(42); // Fixed seed for reproducible tests
    }

    @Test
    @DisplayName("Test Rastrigin minimization with MAP-Elites")
    public void testRastriginMinimization() {
        // Comparator: smaller fitness is better -> compare(b, a)
        Comparator<Solution<double[]>> comparator = (a, b) -> {
            double fa = (Double) a.fitness().get("rastrigin");
            double fb = (Double) b.fitness().get("rastrigin");
            return Double.compare(fb, fa);
        };

        Population<double[]> repository = new DefaultPopulation<>(
                comparator, POPULATION_SIZE, ARCHIVE_SIZE, NUM_ISLANDS);

        List<String> featureDims = Arrays.asList("rastrigin", Constants.DIVERSITY);
        
        MAPElites<double[]> mapElites = new MAPElites<>(
                repository,
                new Migration<>(MIGRATION_INTERVAL, 0.1, repository),
                createFitnessFunction(),
                createEvolutionOperator(),
                createInitialSolutionGenerator(),
                createSelectionFunction(repository),
                createStopCondition(),
                ScaleMethod.MIN_MAX,
                featureDims, 20);
        mapElites.addListener(new MAPElitesLoggingListener<>());
        mapElites.run(ITERATIONS);

        List<Solution<double[]>> all = repository.findAll();
        assertFalse(all.isEmpty(), "Should have found some solutions");

        // Validate bounds and compute best fitness
        double best = Double.POSITIVE_INFINITY;
        for (Solution<double[]> s : all) {
            double[] x = s.solution();
            for (double v : x) {
                assertTrue(v >= LOWER && v <= UPPER, "Variables must stay within bounds");
            }
            double f = (Double) s.fitness().get("rastrigin");
            best = Math.min(best, f);
        }

        System.out.println("Rastrigin best fitness: " + best);
        // Starting random expected ~ ~187 for DIM=10; ensure improvement
        assertTrue(best < 150.0, "Best Rastrigin value should show improvement, got: " + best);
    }

    @Test
    @DisplayName("Test Rastrigin convergence with different parameters")
    public void testRastriginConvergence() {
        // Test with smaller dimension for faster convergence
        final int smallDim = 5;
        
        Comparator<Solution<double[]>> comparator = (a, b) -> {
            double fa = (Double) a.fitness().get("rastrigin");
            double fb = (Double) b.fitness().get("rastrigin");
            return Double.compare(fb, fa);
        };

        Population<double[]> repository = new DefaultPopulation<>(
                comparator, 100, 60, 2);

        List<String> featureDims = Arrays.asList("rastrigin", Constants.DIVERSITY);

        MAPElites<double[]> mapElites = new MAPElites<>(
                repository,
                new Migration<>(50, 0.1, repository),
                createFitnessFunctionForDim(smallDim),
                createEvolutionOperatorForDim(smallDim),
                createInitialSolutionGeneratorForDim(smallDim),
                createSelectionFunction(repository),
                createStopCondition(),
                ScaleMethod.MIN_MAX,
                featureDims, 10);

        mapElites.run(250);

        List<Solution<double[]>> solutions = repository.findAll();
        assertFalse(solutions.isEmpty(), "Should have solutions");

        double bestFitness = solutions.stream()
            .mapToDouble(sol -> (Double) sol.fitness().get("rastrigin"))
            .min()
            .orElse(Double.POSITIVE_INFINITY);

        // For smaller dimension, we should achieve better convergence
        assertTrue(bestFitness < 50.0, "Should achieve better fitness with smaller dimension");
    }

    @Test
    @DisplayName("Test Rastrigin exploration and diversity")
    public void testRastriginExploration() {
        Comparator<Solution<double[]>> comparator = (a, b) -> {
            double fa = (Double) a.fitness().get("rastrigin");
            double fb = (Double) b.fitness().get("rastrigin");
            return Double.compare(fb, fa);
        };

        Population<double[]> repository = new DefaultPopulation<>(
                comparator, 50, 30, 2);

        List<String> featureDims = Arrays.asList("rastrigin", Constants.DIVERSITY);

        MAPElites<double[]> mapElites = new MAPElites<>(
                repository,
                new Migration<>(30, 0.2, repository),
                createFitnessFunction(),
                createEvolutionOperator(),
                createInitialSolutionGenerator(),
                createSelectionFunction(repository),
                createStopCondition(),
                ScaleMethod.MIN_MAX, featureDims, 8);

        mapElites.run(100);

        List<Solution<double[]>> solutions = repository.findAll();
        
        // Check that we maintain diverse solutions
        Set<String> solutionStrings = new HashSet<>();
        for (Solution<double[]> sol : solutions) {
            solutionStrings.add(Arrays.toString(sol.solution()));
        }

        assertTrue(solutionStrings.size() > 5, "Should maintain diverse solutions");

        // Check diversity values
        List<Double> diversityValues = solutions.stream()
            .map(sol -> (Double) sol.fitness().get(Constants.DIVERSITY))
            .toList();

        double minDiversity = diversityValues.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double maxDiversity = diversityValues.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

        assertTrue(maxDiversity > minDiversity + 1.0, "Should explore diverse regions");
    }

    private Predicate<Solution<double[]>> createStopCondition() {
        // For minimization: stop when we reach a sufficiently low rastrigin value.
        return solution -> {
            Object v = solution.fitness().get("rastrigin");
            if (v == null) return false;
            double fitness = (Double) v;
            return fitness <= 5.0;
        };
    }

    private Function<double[], Map<String, Object>> createFitnessFunction() {
        return createFitnessFunctionForDim(DIM);
    }

    private Function<double[], Map<String, Object>> createFitnessFunctionForDim(int dimension) {
        return x -> {
            double f = rastrigin(x);
            double diversity = l2Norm(x); // distance from origin encourages exploration
            Map<String, Object> m = new HashMap<>();
            m.put("rastrigin", f);
            m.put(Constants.DIVERSITY, diversity);
            return m;
        };
    }

    private Function<List<Solution<double[]>>, double[]> createEvolutionOperator() {
        return createEvolutionOperatorForDim(DIM);
    }

    private Function<List<Solution<double[]>>, double[]> createEvolutionOperatorForDim(int dimension) {
        return parents -> {
            if (parents.isEmpty()) return randomVector(dimension);

            if (random.nextDouble() < CROSSOVER_RATE && parents.size() >= 2) {
                var p1 = parents.get(random.nextInt(parents.size())).solution();
                var p2 = parents.get(random.nextInt(parents.size())).solution();
                return mutate(crossover(p1, p2));
            }
            var p = parents.get(random.nextInt(parents.size())).solution();
            return mutate(p.clone());
        };
    }

    private Supplier<List<double[]>> createInitialSolutionGenerator() {
        return createInitialSolutionGeneratorForDim(DIM);
    }

    private Supplier<List<double[]>> createInitialSolutionGeneratorForDim(int dimension) {
        return () -> {
            List<double[]> init = new ArrayList<>();
            for (int i = 0; i < NUM_ISLANDS * 10; i++) init.add(randomVector(dimension));
            return init;
        };
    }

    private Function<Island, List<Solution<double[]>>> createSelectionFunction(Population<double[]> repository) {
        return island -> {
            List<UUID> ids = new ArrayList<>(island.archive());
            if (ids.isEmpty()) return Collections.emptyList();
            int k = Math.min(NUM_INSPIRATIONS, ids.size());
            List<Solution<double[]>> out = new ArrayList<>();
            for (int i = 0; i < k; i++) {
                UUID id = ids.get(random.nextInt(ids.size()));
                Solution<double[]> s = repository.findById(id);
                if (s != null) out.add(s);
            }
            return out;
        };
    }

    private double[] randomVector(int dimension) {
        double[] x = new double[dimension];
        for (int i = 0; i < dimension; i++) x[i] = LOWER + (UPPER - LOWER) * random.nextDouble();
        return x;
    }

    private double[] crossover(double[] a, double[] b) {
        double[] c = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            // Blend crossover (alpha=0.5 uniform)
            double w = random.nextDouble();
            c[i] = clamp(w * a[i] + (1 - w) * b[i]);
        }
        return c;
    }

    private double[] mutate(double[] x) {
        for (int i = 0; i < x.length; i++) {
            if (random.nextDouble() < MUTATION_RATE) {
                x[i] = clamp(x[i] + random.nextGaussian() * MUTATION_SCALE);
            }
        }
        return x;
    }

    private double clamp(double v) { return Math.max(LOWER, Math.min(UPPER, v)); }

    private static double rastrigin(double[] x) {
        double A = 10.0;
        double sum = A * x.length;
        for (double xi : x) {
            sum += xi * xi - A * Math.cos(2 * Math.PI * xi);
        }
        return sum;
    }

    private static double l2Norm(double[] x) {
        double s = 0.0;
        for (double v : x) s += v * v;
        return Math.sqrt(s);
    }
}
