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
import openevolve.mapelites.ParetoComparator;
import openevolve.mapelites.Population;
import openevolve.mapelites.FeatureScaler.ScaleMethod;
import openevolve.mapelites.Population.Island;
import openevolve.mapelites.Population.Solution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Multi-objective MAP-Elites integration tests on the DTLZ2 domain (minimization). DTLZ2 supports
 * an arbitrary number of objectives M >= 2. We cover M=3 and M=4. Decision variables x_i in [0,1].
 * Pareto front satisfies sum_m f_m^2 = 1 when g=0.
 */
@DisplayName("DTLZ2 Multi-Objective Problem Integration Tests")
public class DTLZ2Test {

    private static final int DECISION_VARIABLES = 12; // n (commonly M-1 + k, here k ~ 10)
    private static final int POPULATION_SIZE = 160;
    private static final int ARCHIVE_SIZE = 100;
    private static final int NUM_ISLANDS = 4;
    private static final int NUM_INSPIRATIONS = 3;
    private static final int ITERATIONS = 180; // keep moderate for CI
    private static final int MIGRATION_INTERVAL = 60;
    private static final double MUTATION_RATE = 0.15;
    private static final double MUTATION_SCALE = 0.2; // stddev of Gaussian step
    private static final double CROSSOVER_RATE = 0.7;

    private Random random;

    @BeforeEach
    void setUp() {
        random = new Random(42); // Fixed seed for reproducible tests
    }

    @Test
    @DisplayName("DTLZ2 with 3 objectives (M=3)")
    public void testDTLZ2_M3() {
        final int M = 3;

        // All objectives are minimized.
        boolean[] maximize = new boolean[M];
        Arrays.fill(maximize, false);

        // Fitness extractor returns f1..fM in a stable order.
        Function<Solution<double[]>, double[]> objectiveExtractor = s -> {
            Map<String, Object> f = s.fitness();
            double[] arr = new double[M];
            for (int m = 0; m < M; m++)
                arr[m] = (Double) f.get("f" + (m + 1));
            return arr;
        };

        ParetoComparator<double[]> pareto = new ParetoComparator<>(maximize, objectiveExtractor);

        Population<double[]> repository = new DefaultPopulation<>(pareto, POPULATION_SIZE,
                ARCHIVE_SIZE, NUM_ISLANDS);

        // Features: all objectives + diversity
        List<String> featureDims = new ArrayList<>();
        for (int m = 1; m <= M; m++)
            featureDims.add("f" + m);
        featureDims.add(Constants.DIVERSITY);

        MAPElites<double[]> mapElites = new MAPElites<>(repository,
                new Migration<>(MIGRATION_INTERVAL, 0.1, repository), createDTLZ2FitnessFunction(M),
                createEvolutionOperator(M), createInitialSolutionGenerator(),
                createSelectionFunction(repository), createStopCondition(M), ScaleMethod.MIN_MAX, featureDims, 6);
        mapElites.addListener(new MAPElitesLoggingListener<>());

        mapElites.run(ITERATIONS);

        // Basic validations
        List<Solution<double[]>> all = repository.findAll();
        assertFalse(all.isEmpty(), "Should produce solutions");

        // Variables must remain within [0,1]
        for (Solution<double[]> s : all) {
            for (double v : s.solution())
                assertTrue(v >= 0.0 && v <= 1.0);
        }

        // Check that we have some spread in objective values
        double minF1 =
                all.stream().mapToDouble(s -> (Double) s.fitness().get("f1")).min().orElse(1.0);
        double maxF1 =
                all.stream().mapToDouble(s -> (Double) s.fitness().get("f1")).max().orElse(0.0);
        assertTrue(maxF1 - minF1 > 0.1, "Should cover a range in f1");

        // Check closeness to unit sphere (Pareto front) for a few top nondominated solutions
        List<Solution<double[]>> paretoFront = findParetoFront(all, pareto);
        assertFalse(paretoFront.isEmpty(), "Should find nondominated solutions");

        double avgSphereError = paretoFront.stream().limit(20).mapToDouble(s -> {
            double sumSq = 0.0;
            for (int m = 1; m <= M; m++) {
                double fm = (Double) s.fitness().get("f" + m);
                sumSq += fm * fm;
            }
            return Math.abs(1.0 - sumSq);
        }).average().orElse(1.0);
        // Loose threshold, we only expect progress towards front
        assertTrue(avgSphereError < 1.5,
                "Nondominated set should roughly approach the DTLZ2 front");
    }

    @Test
    @DisplayName("DTLZ2 with 4 objectives (M=4)")
    public void testDTLZ2_M4() {
        final int M = 4;

        boolean[] maximize = new boolean[M];
        Arrays.fill(maximize, false);
        Function<Solution<double[]>, double[]> objectiveExtractor = s -> {
            Map<String, Object> f = s.fitness();
            double[] arr = new double[M];
            for (int m = 0; m < M; m++)
                arr[m] = (Double) f.get("f" + (m + 1));
            return arr;
        };
        ParetoComparator<double[]> pareto = new ParetoComparator<>(maximize, objectiveExtractor);

        Population<double[]> repository = new DefaultPopulation<>(pareto, POPULATION_SIZE,
                ARCHIVE_SIZE, NUM_ISLANDS);

        List<String> featureDims = new ArrayList<>();
        for (int m = 1; m <= M; m++)
            featureDims.add("f" + m);
        featureDims.add(Constants.DIVERSITY);

        MAPElites<double[]> mapElites = new MAPElites<>(repository,
                new Migration<>(MIGRATION_INTERVAL, 0.1, repository), createDTLZ2FitnessFunction(M),
                createEvolutionOperator(M), createInitialSolutionGenerator(),
                createSelectionFunction(repository), createStopCondition(M), ScaleMethod.MIN_MAX,featureDims, 5);

        mapElites.run(ITERATIONS);

        List<Solution<double[]>> all = repository.findAll();
        assertFalse(all.isEmpty());
        // Verify variables in bounds
        for (Solution<double[]> s : all) {
            for (double v : s.solution())
                assertTrue(v >= 0.0 && v <= 1.0);
        }
        // Ensure at least a small nondominated set
        List<Solution<double[]>> paretoFront = findParetoFront(all, pareto);
        assertTrue(paretoFront.size() >= 2, "Should find multiple nondominated solutions");
    }

    private Predicate<Solution<double[]>> createStopCondition(int M) {
        return s -> {
            Map<String, Object> f = s.fitness();
            double[] arr = new double[M];
            for (int m = 0; m < M; m++) {
                Object o = f.get("f" + (m + 1));
                arr[m] = ((Number) o).doubleValue();
            }
            // stop if all objectives are below the threshold (minimization)
            final double THRESHOLD = 0.1;
            for (double v : arr) {
                if (!(v < THRESHOLD))
                    return false;
            }
            return true;
        };
    }

    private Function<double[], Map<String, Object>> createDTLZ2FitnessFunction(int M) {
        return x -> {
            // Validate and clamp bounds (defensive, but keep within [0,1])
            double[] xc = x.clone();
            for (int i = 0; i < xc.length; i++) {
                if (xc[i] < 0.0)
                    xc[i] = 0.0;
                if (xc[i] > 1.0)
                    xc[i] = 1.0;
            }

            // DTLZ2 definition
            int n = xc.length;
            int k = n - M + 1;
            double g = 0.0;
            for (int i = n - k; i < n; i++) {
                double d = xc[i] - 0.5;
                g += d * d;
            }
            double[] f = new double[M];
            for (int m = 1; m <= M; m++) {
                double val = 1.0 + g;
                // product of cos over first (M-m) variables
                for (int i = 0; i < M - m; i++) {
                    val *= Math.cos(0.5 * Math.PI * xc[i]);
                }
                if (m > 1) {
                    int idx = M - m; // 0-based index
                    val *= Math.sin(0.5 * Math.PI * xc[idx]);
                }
                f[m - 1] = val;
            }

            // Diversity measure: distance from center in decision space
            double diversity = 0.0;
            for (double v : xc) {
                double d = v - 0.5;
                diversity += d * d;
            }
            diversity = Math.sqrt(diversity);

            Map<String, Object> map = new HashMap<>();
            for (int m = 1; m <= M; m++)
                map.put("f" + m, f[m - 1]);
            map.put(Constants.DIVERSITY, diversity);
            return map;
        };
    }

    private Function<List<Solution<double[]>>, double[]> createEvolutionOperator(int M) {
        return parents -> {
            if (parents.isEmpty())
                return randomVector(DECISION_VARIABLES);

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
        return () -> {
            List<double[]> init = new ArrayList<>();
            for (int i = 0; i < NUM_ISLANDS * 10; i++)
                init.add(randomVector(DECISION_VARIABLES));
            return init;
        };
    }

    private Function<Island, List<Solution<double[]>>> createSelectionFunction(
            Population<double[]> repository) {
        return island -> {
            List<UUID> ids = new ArrayList<>(island.archive());
            if (ids.isEmpty())
                return Collections.emptyList();
            int k = Math.min(NUM_INSPIRATIONS, ids.size());
            List<Solution<double[]>> out = new ArrayList<>();
            for (int i = 0; i < k; i++) {
                UUID id = ids.get(random.nextInt(ids.size()));
                Solution<double[]> s = repository.findById(id);
                if (s != null)
                    out.add(s);
            }
            return out;
        };
    }

    private double[] randomVector(int dimension) {
        double[] x = new double[dimension];
        for (int i = 0; i < dimension; i++)
            x[i] = random.nextDouble();
        return x;
    }

    private double[] crossover(double[] a, double[] b) {
        // Uniform blend crossover in [0,1]
        double[] c = new double[a.length];
        for (int i = 0; i < a.length; i++) {
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

    private double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private List<Solution<double[]>> findParetoFront(List<Solution<double[]>> solutions,
            ParetoComparator<double[]> comparator) {
        List<Solution<double[]>> front = new ArrayList<>();
        for (Solution<double[]> candidate : solutions) {
            boolean dominated = false;
            for (Solution<double[]> other : solutions) {
                if (other == candidate)
                    continue;
                // comparator.compare(a,b) < 0 means a better than b? In ZDT1 they use
                // repository.compare
                // We'll ask comparator directly via repository.compare when available, but here we
                // approximate
                // by building the double[] and using comparator.compare on Solutions via
                // objectiveExtractor indirectly.
                // Since we don't have direct access, we compare using repository in caller when
                // needed.
                // Fallback: mark dominated if all objectives are >= and one >
                double[] a = extractObjectives(candidate, comparator);
                double[] b = extractObjectives(other, comparator);
                if (dominates(b, a)) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated)
                front.add(candidate);
        }
        return front;
    }

    private static boolean dominates(double[] a, double[] b) {
        boolean strictlyBetter = false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] > b[i])
                return false; // minimization
            if (a[i] < b[i])
                strictlyBetter = true;
        }
        return strictlyBetter;
    }

    private static double[] extractObjectives(Solution<double[]> s,
            ParetoComparator<double[]> comparator) {
        // The comparator was built with an extractor that expects keys f1..fM. Reconstruct that
        // array.
        Map<String, Object> f = s.fitness();
        // Detect M by counting keys f1..fK
        int M = 0;
        while (f.containsKey("f" + (M + 1)))
            M++;
        double[] arr = new double[M];
        for (int m = 1; m <= M; m++)
            arr[m - 1] = (Double) f.get("f" + m);
        return arr;
    }
}
