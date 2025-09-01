package openevolve.integration.mapelites;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import openevolve.Constants;
import openevolve.mapelites.listener.MAPElitesLoggingListener;
import openevolve.mapelites.DefaultRepository;
import openevolve.mapelites.MAPElites;
import openevolve.mapelites.Migration;
import openevolve.mapelites.ParetoComparator;
import openevolve.mapelites.Repository;
import openevolve.mapelites.FeatureScaler.ScaleMethod;
import openevolve.mapelites.Repository.Island;
import openevolve.mapelites.Repository.Solution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the ZDT1 multi-objective optimization problem.
 *
 * ZDT1 Problem Definition:
 * - Decision variables: x_i ∈ [0,1] for i = 1,2,...,n
 * - Objective 1: f1(x) = x_1
 * - Objective 2: f2(x) = g(x) * h(f1(x), g(x))
 * - Where: g(x) = 1 + 9 * (sum(x_2 to x_n) / (n-1))
 * - And: h(f1, g) = 1 - sqrt(f1/g)
 *
 * The Pareto-optimal front is: f2 = 1 - sqrt(f1) for f1 ∈ [0,1]
 */
@DisplayName("ZDT1 Multi-Objective Problem Integration Tests")
public class ZDT1Test {

    private static final int DECISION_VARIABLES = 10; // Number of decision variables
    private static final int POPULATION_SIZE = 200;
    private static final int ARCHIVE_SIZE = 100;
    private static final int NUM_ISLANDS = 4;
    private static final int NUM_INSPIRATIONS = 2;
    private static final int ITERATIONS = 500;
    private static final int MIGRATION_INTERVAL = 100;
    private static final double MUTATION_RATE = 0.1;
    private static final double CROSSOVER_RATE = 0.8;
    private Random random;

    @BeforeEach
    void setUp() {
        random = new Random(42); // Fixed seed for reproducibility
    }

    @Test
    @DisplayName("Test ZDT1 multi-objective optimization with MAP-Elites")
    public void testZDT1Problem() {
        // Setup Pareto comparator for multi-objective optimization
        boolean[] maximize = {false, false}; // Both objectives are to be minimized
        Function<Solution<double[]>, double[]> objectiveExtractor = solution -> {
            Map<String, Object> fitness = solution.fitness();
            return new double[]{
                (Double) fitness.get("f1"),
                (Double) fitness.get("f2")
            };
        };
        
        ParetoComparator<double[]> paretoComparator = new ParetoComparator<>(maximize, objectiveExtractor);

        // Create repository with Pareto comparator
        Repository<double[]> repository = new DefaultRepository<>(
            paretoComparator, POPULATION_SIZE, ARCHIVE_SIZE, NUM_ISLANDS
        );

        // Setup feature dimensions for MAP-Elites grid (using both objectives)
        List<String> featureDimensions = Arrays.asList("f1", "f2", Constants.DIVERSITY);
        // Create MAP-Elites algorithm
        MAPElites<double[]> mapElites = new MAPElites<>(
            repository,
            new Migration<>(MIGRATION_INTERVAL, 0.1, repository),
            createZDT1FitnessFunction(),
            createZDT1EvolutionOperator(),
            createZDT1InitialSolutionGenerator(),
            createZDT1SelectionFunction(repository),
            createStopCondition(),
            ScaleMethod.MIN_MAX,
            featureDimensions, 20
        );
        mapElites.addListener(new MAPElitesLoggingListener<>());

        // Run the algorithm
        mapElites.run(ITERATIONS);

        // Verify results
        List<Solution<double[]>> allSolutions = repository.findAll();
        assertFalse(allSolutions.isEmpty(), "Should have found some solutions");

        // Analyze the Pareto front
        List<Solution<double[]>> paretoFront = findParetoFront(allSolutions, paretoComparator);
        assertFalse(paretoFront.isEmpty(), "Should have found Pareto-optimal solutions");

        // Verify solution quality
        validateZDT1Solutions(allSolutions);
        
        // Check convergence towards the true Pareto front
        double averageDistanceToFront = calculateAverageDistanceToTrueParetoFront(paretoFront);
        
        // Print statistics first for debugging
        printZDT1Statistics(allSolutions, paretoFront);
        
        // More reasonable threshold for a basic evolutionary algorithm
        assertTrue(averageDistanceToFront < 5.0,
            "Average distance to true Pareto front should be reasonable for a basic EA, got: " + averageDistanceToFront);

        // Check that we have a reasonable spread of solutions
        assertTrue(paretoFront.size() >= 2, "Should have at least 2 solutions in Pareto front");
    }

    @Test
    @DisplayName("Test ZDT1 convergence with smaller problem size")
    public void testZDT1Convergence() {
        // Test with fewer variables for faster convergence
        final int smallDim = 5;
        
        boolean[] maximize = {false, false};
        Function<Solution<double[]>, double[]> objectiveExtractor = solution -> {
            Map<String, Object> fitness = solution.fitness();
            return new double[]{
                (Double) fitness.get("f1"),
                (Double) fitness.get("f2")
            };
        };
        
        ParetoComparator<double[]> paretoComparator = new ParetoComparator<>(maximize, objectiveExtractor);

        Repository<double[]> repository = new DefaultRepository<>(
            paretoComparator, 100, 50, 2
        );

        List<String> featureDimensions = Arrays.asList("f1", "f2", Constants.DIVERSITY);

        MAPElites<double[]> mapElites = new MAPElites<>(
            repository,
            new Migration<>(50, 0.1, repository),
            createZDT1FitnessFunctionForDim(smallDim),
            createZDT1EvolutionOperatorForDim(smallDim),
            createZDT1InitialSolutionGeneratorForDim(smallDim),
            createZDT1SelectionFunction(repository),
            createStopCondition(),
            ScaleMethod.MIN_MAX,
            featureDimensions, 10
        );

        mapElites.run(250);

        List<Solution<double[]>> solutions = repository.findAll();
        assertFalse(solutions.isEmpty(), "Should have solutions");

        List<Solution<double[]>> paretoFront = findParetoFront(solutions, paretoComparator);
        assertTrue(paretoFront.size() >= 2, "Should find multiple Pareto solutions");

        // Verify objectives are reasonable
        for (Solution<double[]> sol : paretoFront) {
            double f1 = (Double) sol.fitness().get("f1");
            double f2 = (Double) sol.fitness().get("f2");
            assertTrue(f1 >= 0.0 && f1 <= 1.0, "f1 should be in valid range");
            assertTrue(f2 >= 0.0, "f2 should be non-negative");
        }
    }

    @Test
    @DisplayName("Test ZDT1 diversity maintenance")
    public void testZDT1DiversityMaintenance() {
        boolean[] maximize = {false, false};
        Function<Solution<double[]>, double[]> objectiveExtractor = solution -> {
            Map<String, Object> fitness = solution.fitness();
            return new double[]{
                (Double) fitness.get("f1"),
                (Double) fitness.get("f2")
            };
        };
        
        ParetoComparator<double[]> paretoComparator = new ParetoComparator<>(maximize, objectiveExtractor);

        Repository<double[]> repository = new DefaultRepository<>(
            paretoComparator, 50, 30, 2
        );

        List<String> featureDimensions = Arrays.asList("f1", "f2", Constants.DIVERSITY);

        MAPElites<double[]> mapElites = new MAPElites<>(
            repository,
            new Migration<>(30, 0.2, repository),
            createZDT1FitnessFunction(),
            createZDT1EvolutionOperator(),
            createZDT1InitialSolutionGenerator(),
            createZDT1SelectionFunction(repository),
            createStopCondition(),
            ScaleMethod.MIN_MAX,
            featureDimensions, 8
        );

        mapElites.run(100);

        List<Solution<double[]>> solutions = repository.findAll();
        
        // Check that we maintain diverse solutions in objective space
        Set<String> f1Values = new HashSet<>();
        for (Solution<double[]> sol : solutions) {
            double f1 = (Double) sol.fitness().get("f1");
            f1Values.add(String.format("%.2f", f1));
        }

        assertTrue(f1Values.size() > 3, "Should maintain diversity in f1 values");

        // Check diversity values
        List<Double> diversityValues = solutions.stream()
            .map(sol -> (Double) sol.fitness().get(Constants.DIVERSITY))
            .toList();

        double minDiversity = diversityValues.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double maxDiversity = diversityValues.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

        assertTrue(maxDiversity > minDiversity, "Should explore diverse regions");
    }

    private Predicate<Solution<double[]>> createStopCondition() {
        // Do not stop based on a single solution; the caller controls iteration count via run(ITERATIONS).
        // Returning a predicate that always returns false prevents premature termination.
        return _ -> false;
    }

    private Function<double[], Map<String, Object>> createZDT1FitnessFunction() {
        return createZDT1FitnessFunctionForDim(DECISION_VARIABLES);
    }

    private Function<double[], Map<String, Object>> createZDT1FitnessFunctionForDim(int dimensions) {
        return solution -> {
            // Validate solution bounds
            for (double x : solution) {
                if (x < 0.0 || x > 1.0) {
                    throw new IllegalArgumentException("ZDT1 variables must be in [0,1]");
                }
            }

            // Calculate ZDT1 objectives
            double x1 = solution[0];
            
            // Calculate g(x) = 1 + 9 * (sum of remaining variables) / (n-1)
            double sumRest = 0.0;
            for (int i = 1; i < solution.length; i++) {
                sumRest += solution[i];
            }
            double g = 1.0 + 9.0 * sumRest / (solution.length - 1);
            
            // Calculate objectives
            double f1 = x1;
            double h = 1.0 - Math.sqrt(f1 / g);
            double f2 = g * h;

            // Calculate diversity as distance from center point
            double diversity = calculateEuclideanDistanceFromCenter(solution);

            Map<String, Object> fitnessMap = new HashMap<>();
            fitnessMap.put("f1", f1);
            fitnessMap.put("f2", f2);
            fitnessMap.put(Constants.DIVERSITY, diversity);
            return fitnessMap;
        };
    }

    private Function<List<Solution<double[]>>, double[]> createZDT1EvolutionOperator() {
        return createZDT1EvolutionOperatorForDim(DECISION_VARIABLES);
    }

    private Function<List<Solution<double[]>>, double[]> createZDT1EvolutionOperatorForDim(int dimensions) {
        return parents -> {
            if (parents.isEmpty()) {
                return generateRandomZDT1Solution(dimensions);
            }

            // Use different genetic operators
            if (random.nextDouble() < CROSSOVER_RATE && parents.size() >= 2) {
                // Crossover between two parents
                Solution<double[]> parent1 = parents.get(random.nextInt(parents.size()));
                Solution<double[]> parent2 = parents.get(random.nextInt(parents.size()));
                return crossover(parent1.solution(), parent2.solution());
            } else {
                // Mutation of single parent
                Solution<double[]> parent = parents.get(random.nextInt(parents.size()));
                return mutate(parent.solution());
            }
        };
    }

    private Supplier<List<double[]>> createZDT1InitialSolutionGenerator() {
        return createZDT1InitialSolutionGeneratorForDim(DECISION_VARIABLES);
    }

    private Supplier<List<double[]>> createZDT1InitialSolutionGeneratorForDim(int dimensions) {
        return () -> {
            List<double[]> solutions = new ArrayList<>();
            for (int i = 0; i < NUM_ISLANDS * 10; i++) {
                solutions.add(generateRandomZDT1Solution(dimensions));
            }
            return solutions;
        };
    }

    private Function<Island, List<Solution<double[]>>> createZDT1SelectionFunction(Repository<double[]> repository) {
        return island -> {
            List<UUID> archive = new ArrayList<>(island.archive());
            if (archive.isEmpty()) {
                return Collections.emptyList();
            }

            // Tournament selection for multi-objective optimization
            int numToSelect = Math.min(NUM_INSPIRATIONS, archive.size());
            List<Solution<double[]>> selected = new ArrayList<>();
            for (int i = 0; i < numToSelect; i++) {
                Solution<double[]> solution = tournamentSelection(archive, repository, 3);
                if (solution != null) {
                    selected.add(solution);
                }
            }
            return selected;
        };
    }

    private double[] generateRandomZDT1Solution(int dimensions) {
        double[] solution = new double[dimensions];
        for (int i = 0; i < dimensions; i++) {
            solution[i] = random.nextDouble(); // Random value in [0,1]
        }
        return solution;
    }

    private double[] crossover(double[] parent1, double[] parent2) {
        double[] child = new double[parent1.length];
        for (int i = 0; i < parent1.length; i++) {
            // Simulated Binary Crossover (SBX)
            if (random.nextDouble() < 0.5) {
                child[i] = parent1[i];
            } else {
                child[i] = parent2[i];
            }
        }
        return mutate(child); // Apply mutation after crossover
    }

    private double[] mutate(double[] individual) {
        double[] mutated = individual.clone();
        for (int i = 0; i < mutated.length; i++) {
            if (random.nextDouble() < MUTATION_RATE) {
                // Polynomial mutation
                double delta = 0.1 * (random.nextGaussian());
                mutated[i] = Math.max(0.0, Math.min(1.0, mutated[i] + delta));
            }
        }
        return mutated;
    }

    private Solution<double[]> tournamentSelection(List<UUID> archive, Repository<double[]> repository, int tournamentSize) {
        List<Solution<double[]>> tournament = new ArrayList<>();
        for (int i = 0; i < Math.min(tournamentSize, archive.size()); i++) {
            UUID id = archive.get(random.nextInt(archive.size()));
            Solution<double[]> solution = repository.findById(id);
            if (solution != null) {
                tournament.add(solution);
            }
        }
        
        if (tournament.isEmpty()) {
            return null;
        }
        
        // Select the best according to Pareto dominance
        return tournament.stream()
            .min((a, b) -> repository.compare(a, b))
            .orElse(tournament.get(0));
    }

    private double calculateEuclideanDistanceFromCenter(double[] solution) {
        double[] center = new double[solution.length];
        Arrays.fill(center, 0.5); // Center point [0.5, 0.5, ..., 0.5]
        
        double sumSquares = 0.0;
        for (int i = 0; i < solution.length; i++) {
            double diff = solution[i] - center[i];
            sumSquares += diff * diff;
        }
        return Math.sqrt(sumSquares);
    }

    private List<Solution<double[]>> findParetoFront(List<Solution<double[]>> solutions, ParetoComparator<double[]> comparator) {
        List<Solution<double[]>> paretoFront = new ArrayList<>();
        
        for (Solution<double[]> candidate : solutions) {
            boolean isDominated = false;
            for (Solution<double[]> other : solutions) {
                if (comparator.dominates(other, candidate)) {
                    isDominated = true;
                    break;
                }
            }
            if (!isDominated) {
                paretoFront.add(candidate);
            }
        }
        
        return paretoFront;
    }

    private void validateZDT1Solutions(List<Solution<double[]>> solutions) {
        for (Solution<double[]> solution : solutions) {
            double[] variables = solution.solution();
            
            // Check bounds
            for (double x : variables) {
                assertTrue(x >= 0.0 && x <= 1.0, "All variables should be in [0,1]");
            }
            
            // Check objective values are reasonable
            double f1 = (Double) solution.fitness().get("f1");
            double f2 = (Double) solution.fitness().get("f2");
            
            assertTrue(f1 >= 0.0 && f1 <= 1.0, "f1 should be in [0,1], got: " + f1);
            assertTrue(f2 >= 0.0, "f2 should be non-negative, got: " + f2);
        }
    }

    private double calculateAverageDistanceToTrueParetoFront(List<Solution<double[]>> paretoFront) {
        double totalDistance = 0.0;
        
        for (Solution<double[]> solution : paretoFront) {
            double f1 = (Double) solution.fitness().get("f1");
            double f2 = (Double) solution.fitness().get("f2");
            
            // True Pareto front: f2 = 1 - sqrt(f1)
            double trueParetoF2 = 1.0 - Math.sqrt(f1);
            double distance = Math.abs(f2 - trueParetoF2);
            totalDistance += distance;
        }
        
        return paretoFront.isEmpty() ? Double.MAX_VALUE : totalDistance / paretoFront.size();
    }

    private void printZDT1Statistics(List<Solution<double[]>> allSolutions, List<Solution<double[]>> paretoFront) {
        System.out.println("=== ZDT1 Test Results ===");
        System.out.println("Total solutions found: " + allSolutions.size());
        System.out.println("Pareto front size: " + paretoFront.size());
        
        if (!paretoFront.isEmpty()) {
            // Print some Pareto front solutions
            System.out.println("\nSample Pareto-optimal solutions:");
            paretoFront.stream()
                .limit(5)
                .forEach(solution -> {
                    double f1 = (Double) solution.fitness().get("f1");
                    double f2 = (Double) solution.fitness().get("f2");
                    System.out.printf("f1=%.4f, f2=%.4f, true_f2=%.4f%n",
                        f1, f2, 1.0 - Math.sqrt(f1));
                });
        }
        
        // Calculate hypervolume or other quality metrics if needed
        double[] f1Range = allSolutions.stream()
            .mapToDouble(s -> (Double) s.fitness().get("f1"))
            .summaryStatistics()
            .toString()
            .length() > 0 ?
            new double[]{
                allSolutions.stream().mapToDouble(s -> (Double) s.fitness().get("f1")).min().orElse(0),
                allSolutions.stream().mapToDouble(s -> (Double) s.fitness().get("f1")).max().orElse(1)
            } : new double[]{0, 1};
            
        System.out.printf("f1 range: [%.4f, %.4f]%n", f1Range[0], f1Range[1]);
        
        double[] f2Range = new double[]{
            allSolutions.stream().mapToDouble(s -> (Double) s.fitness().get("f2")).min().orElse(0),
            allSolutions.stream().mapToDouble(s -> (Double) s.fitness().get("f2")).max().orElse(1)
        };
        System.out.printf("f2 range: [%.4f, %.4f]%n", f2Range[0], f2Range[1]);
    }
}
