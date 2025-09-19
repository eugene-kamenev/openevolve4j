package openevolve.integration.mapelites;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OneMax Problem Integration Tests")
public class OneMaxTest {

    private static final int STRING_LENGTH = 20;
    private static final int POPULATION_SIZE = 100;
    private static final int ARCHIVE_SIZE = 50;
    private static final int NUM_ISLANDS = 4;
    private static final int NUM_INSPIRATIONS = 3;
    private static final int ITERATIONS = 200;
    private static final int MIGRATION_INTERVAL = 50;
    private static final double MUTATION_RATE = 0.1;
    private Random random;

    @BeforeEach
    void setUp() {
        random = new Random(42); // Fixed seed for reproducible tests
    }

    @Test
    @DisplayName("Test OneMax problem with MAP-Elites algorithm")
    public void testOneMaxProblem() {
        // Setup comparator for solutions (maximize fitness)
        Comparator<Solution<String>> comparator = (a, b) -> {
            double fitnessA = (Double) a.fitness().get("fitness");
            double fitnessB = (Double) b.fitness().get("fitness");
            return Double.compare(fitnessA, fitnessB);
        };

        // Create repository
        Population<String> repository = new DefaultPopulation<>(
            comparator, POPULATION_SIZE, ARCHIVE_SIZE, NUM_ISLANDS
        );

        // Setup feature dimensions for MAP-Elites grid
        List<String> featureDimensions = Arrays.asList("fitness", Constants.DIVERSITY);

        // Create MAP-Elites algorithm
        MAPElites<String> mapElites = new MAPElites<>(
            repository,
            new Migration<>(MIGRATION_INTERVAL, 0.1, repository),
            createFitnessFunction(),
            createEvolutionOperator(),
            createInitialSolutionGenerator(),
            createSelectionFunction(repository),
            createStopCondition(),
            ScaleMethod.MIN_MAX,
            featureDimensions, 10
        );
        mapElites.addListener(new MAPElitesLoggingListener<>());

        // Run the algorithm
        mapElites.run(ITERATIONS);

        // Verify results
        List<Solution<String>> allSolutions = repository.findAll();
        assertFalse(allSolutions.isEmpty(), "Should have found some solutions");

        // Check if we found the optimal solution (all 1s)
        String optimalSolution = "1".repeat(STRING_LENGTH);
        boolean foundOptimal = allSolutions.stream()
            .anyMatch(sol -> sol.solution().equals(optimalSolution));

        if (foundOptimal) {
            System.out.println("Found optimal solution: " + optimalSolution);
        }

        // Verify fitness values are reasonable
        double maxFitness = allSolutions.stream()
            .mapToDouble(sol -> (Double) sol.fitness().get("fitness"))
            .max()
            .orElse(0.0);

        assertTrue(maxFitness > STRING_LENGTH * 0.7,
            "Should achieve at least 70% of optimal fitness, got: " + maxFitness);

        // Print some statistics
        System.out.println("Total solutions found: " + allSolutions.size());
        System.out.println("Max fitness achieved: " + maxFitness + " / " + STRING_LENGTH);
        System.out.println("Islands populated: " + repository.findAllIslands().stream()
            .mapToInt(island -> repository.countByIslandId(island.id()))
            .sum());

        // Print archive for debugging
        mapElites.printArchive();
    }

    @Test
    @DisplayName("Test OneMax convergence with different parameters")
    public void testOneMaxConvergence() {
        // Test with smaller problem for faster convergence
        final int shortLength = 10;
        
        // Create simpler setup
        Comparator<Solution<String>> comparator = (a, b) -> {
            double fitnessA = (Double) a.fitness().get("fitness");
            double fitnessB = (Double) b.fitness().get("fitness");
            return Double.compare(fitnessA, fitnessB);
        };

        Population<String> repository = new DefaultPopulation<>(
            comparator, 50, 25, 2
        );

        List<String> featureDimensions = Arrays.asList("fitness", Constants.DIVERSITY);

        MAPElites<String> mapElites = new MAPElites<>(
            repository,
            new Migration<>(25, 0.1, repository),
            createFitnessFunctionForLength(shortLength),
            createEvolutionOperatorForLength(shortLength),
            createInitialSolutionGeneratorForLength(shortLength),
            createSelectionFunction(repository),
            createStopCondition(),
            ScaleMethod.MIN_MAX,
            featureDimensions, 5
        );

        // Run for fewer iterations
        mapElites.run(100);

        // Verify convergence
        List<Solution<String>> solutions = repository.findAll();
        assertFalse(solutions.isEmpty(), "Should have solutions");

        double maxFitness = solutions.stream()
            .mapToDouble(sol -> (Double) sol.fitness().get("fitness"))
            .max()
            .orElse(0.0);

        assertTrue(maxFitness >= shortLength * 0.8, "Should achieve good fitness");
    }

    @Test
    @DisplayName("Test OneMax diversity maintenance")
    public void testOneMaxDiversityMaintenance() {
        // Create setup focused on diversity
        Comparator<Solution<String>> comparator = (a, b) -> {
            double diversityA = (Double) a.fitness().get(Constants.DIVERSITY);
            double diversityB = (Double) b.fitness().get(Constants.DIVERSITY);
            return Double.compare(diversityA, diversityB);
        };

        Population<String> repository = new DefaultPopulation<>(
            comparator, 30, 15, 2
        );

        List<String> featureDimensions = Arrays.asList("fitness", Constants.DIVERSITY);

        MAPElites<String> mapElites = new MAPElites<>(
            repository,
            new Migration<>(20, 0.2, repository),
            createFitnessFunction(),
            createEvolutionOperator(),
            createInitialSolutionGenerator(),
            createSelectionFunction(repository),
            createStopCondition(),
            ScaleMethod.MIN_MAX,
            featureDimensions, 5
        );

        mapElites.run(50);

        // Check diversity of solutions
        List<Solution<String>> solutions = repository.findAll();
        Set<String> uniqueSolutions = solutions.stream()
            .map(Solution::solution)
            .collect(Collectors.toSet());

        assertTrue(uniqueSolutions.size() > 1, "Should maintain diverse solutions");

        // Check diversity values
        List<Double> diversityValues = solutions.stream()
            .map(sol -> (Double) sol.fitness().get(Constants.DIVERSITY))
            .collect(Collectors.toList());

        double minDiversity = diversityValues.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double maxDiversity = diversityValues.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

        assertTrue(maxDiversity > minDiversity, "Should have diversity range");
    }

    private Function<String, Map<String, Object>> createFitnessFunction() {
        return createFitnessFunctionForLength(STRING_LENGTH);
    }

    private Function<String, Map<String, Object>> createFitnessFunctionForLength(int length) {
        return solution -> {
            // Count number of 1s (fitness to maximize)
            double fitness = solution.chars()
                .mapToDouble(c -> c == '1' ? 1.0 : 0.0)
                .sum();

            // Calculate diversity as hamming distance from a reference string
            String reference = "0".repeat(length);
            double diversity = calculateHammingDistance(solution, reference);

            Map<String, Object> fitnessMap = new HashMap<>();
            fitnessMap.put("fitness", fitness);
            fitnessMap.put(Constants.DIVERSITY, diversity);
            return fitnessMap;
        };
    }

    private Predicate<Solution<String>> createStopCondition() {
        return solution -> {
            Map<String, Object> fitnessMap = solution.fitness();
            Object fObj = fitnessMap.get("fitness");
            double fitness = ((Number) fObj).doubleValue();
            String sol = solution.solution();
            return fitness >= sol.length();
        };
    }

    private Function<List<Solution<String>>, String> createEvolutionOperator() {
        return createEvolutionOperatorForLength(STRING_LENGTH);
    }

    private Function<List<Solution<String>>, String> createEvolutionOperatorForLength(int length) {
        return parents -> {
            if (parents.isEmpty()) {
                return generateRandomBinaryString(length);
            }

            // Select the best parent from the list
            Solution<String> parent = parents.stream()
                .max((a, b) -> {
                    double fitnessA = (Double) a.fitness().get("fitness");
                    double fitnessB = (Double) b.fitness().get("fitness");
                    return Double.compare(fitnessA, fitnessB);
                })
                .orElse(parents.get(0));
            
            return mutate(parent.solution());
        };
    }

    private Supplier<List<String>> createInitialSolutionGenerator() {
        return createInitialSolutionGeneratorForLength(STRING_LENGTH);
    }

    private Supplier<List<String>> createInitialSolutionGeneratorForLength(int length) {
        return () -> IntStream.range(0, NUM_ISLANDS * 5)
            .mapToObj(_ -> generateRandomBinaryString(length))
            .collect(Collectors.toList());
    }

    private Function<Island, List<Solution<String>>> createSelectionFunction(Population<String> repository) {
        return island -> {
            List<UUID> archive = new ArrayList<>(island.archive());
            if (archive.isEmpty()) {
                return Collections.emptyList();
            }
            int numToSelect = Math.min(NUM_INSPIRATIONS, archive.size());
            
            return random.ints(0, archive.size())
                .distinct()
                .limit(numToSelect)
                .mapToObj(archive::get)
                .map(repository::findById)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        };
    }

    private String generateRandomBinaryString(int length) {
        return random.ints(length, 0, 2)
            .mapToObj(String::valueOf)
            .collect(Collectors.joining());
    }

    private String mutate(String individual) {
        char[] chars = individual.toCharArray();
        
        for (int i = 0; i < chars.length; i++) {
            if (random.nextDouble() < MUTATION_RATE) {
                chars[i] = chars[i] == '0' ? '1' : '0';
            }
        }
        
        return new String(chars);
    }

    private double calculateHammingDistance(String s1, String s2) {
        if (s1.length() != s2.length()) {
            throw new IllegalArgumentException("Strings must have equal length");
        }
        
        int distance = 0;
        for (int i = 0; i < s1.length(); i++) {
            if (s1.charAt(i) != s2.charAt(i)) {
                distance++;
            }
        }
        return distance;
    }
}
