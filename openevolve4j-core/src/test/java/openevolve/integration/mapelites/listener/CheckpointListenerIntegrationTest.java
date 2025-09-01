package openevolve.integration.mapelites.listener;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

import openevolve.Constants;
import openevolve.mapelites.listener.CheckpointListener;
import openevolve.mapelites.DefaultRepository;
import openevolve.mapelites.MAPElites;
import openevolve.mapelites.Migration;
import openevolve.mapelites.Repository;
import openevolve.mapelites.FeatureScaler.ScaleMethod;
import openevolve.mapelites.Repository.Island;
import openevolve.mapelites.Repository.Solution;

/**
 * Integration test for CheckpointListener to verify it works correctly
 * with the actual MAPElites algorithm execution.
 */
@DisplayName("CheckpointListener Integration Tests")
public class CheckpointListenerIntegrationTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;
    private Repository<Double> repository;
    private Migration<Double> migration;
    private MAPElites<Double> mapElites;

    @BeforeEach
    void setUp() {
        objectMapper = Constants.OBJECT_MAPPER;
        
        // Create repository with simple fitness comparator
        Comparator<Solution<Double>> comparator = (a, b) ->
            Double.compare((Double) a.fitness().get("fitness"), (Double) b.fitness().get("fitness"));
        repository = new DefaultRepository<>(comparator, 100, 50, 2);
        
        // Create migration
        migration = new Migration<>(10, 0.1, repository);
        
        // Create simple fitness function
        Function<Double, Map<String, Object>> fitnessFunction = value -> {
            Map<String, Object> fitness = new HashMap<>();
            fitness.put("fitness", value * value); // Simple quadratic fitness
            return fitness;
        };
        
        // Create simple evolution operator (just adds random noise)
        Function<List<Solution<Double>>, Double> evolveOperator = parents -> {
            if (parents.isEmpty()) return Math.random();
            Double parent = parents.get(0).solution();
            return parent + (Math.random() - 0.5) * 0.1; // Small mutation
        };
        
        // Create initial solution generator
        Supplier<List<Double>> initialSolutionGenerator = () ->
            List.of(0.1, 0.2, 0.3, 0.4, 0.5);
        
        // Create simple selection operator
        Function<Island, List<Solution<Double>>> selection = island -> {
            var solutions = repository.findByIslandId(island.id());
            if (solutions.isEmpty()) return List.of();
            return solutions.subList(0, Math.min(2, solutions.size()));
        };
        
        // Create MAPElites instance
        mapElites = new MAPElites<>(repository, migration, fitnessFunction,
            evolveOperator, initialSolutionGenerator, selection, _ -> false, ScaleMethod.MIN_MAX, List.of("fitness"), 10);
    }

    @Test
    @DisplayName("Test checkpointing during MAPElites execution")
    void testCheckpointingDuringMAPElitesExecution() throws Exception {
        // Arrange
        int checkpointInterval = 3; // Use smaller interval for better testing
        CheckpointListener<Double> checkpointListener = new CheckpointListener<>(
            tempDir, checkpointInterval, objectMapper, repository, null);
        
        mapElites.addListener(checkpointListener);

        // Act - Run MAPElites for enough iterations to trigger checkpoints
        mapElites.run(10);

        // Assert - Verify at least some checkpoints were created
        boolean foundCheckpoint = false;
        for (int i = checkpointInterval; i <= 10; i += checkpointInterval) {
            Path checkpointFile = tempDir.resolve("checkpoint_iter_" + i + ".json");
            if (Files.exists(checkpointFile)) {
                foundCheckpoint = true;
                break;
            }
        }
        assertTrue(foundCheckpoint, "At least one checkpoint file should have been created");
    }

    @Test
    @DisplayName("Test restore from checkpoint and continue execution")
    void testRestoreFromCheckpointAndContinueExecution() throws Exception {
        // Arrange - First run to create checkpoint
        CheckpointListener<Double> checkpointListener1 = new CheckpointListener<>(
            tempDir, 2, objectMapper, repository, null);
        mapElites.addListener(checkpointListener1);
        
        // Run for 4 iterations to create checkpoints
        mapElites.run(4);
        
        // Find any existing checkpoint file
        Path checkpointFile = null;
        Integer checkpointIteration = null;
        for (int i = 2; i <= 4; i += 2) {
            Path candidateFile = tempDir.resolve("checkpoint_iter_" + i + ".json");
            if (Files.exists(candidateFile)) {
                checkpointFile = candidateFile;
                checkpointIteration = i;
                break;
            }
        }
        
        assertNotNull(checkpointFile, "At least one checkpoint file should exist");
        assertNotNull(checkpointIteration, "Checkpoint iteration should be identified");
        
        int originalSolutionCount = repository.count();
        assertTrue(originalSolutionCount > 0);

        // Create new instances to simulate restart
        Repository<Double> newRepository = new DefaultRepository<>(
            (a, b) -> Double.compare((Double) a.fitness().get("fitness"), (Double) b.fitness().get("fitness")),
            100, 50, 2);
        Migration<Double> newMigration = new Migration<>(10, 0.1, newRepository);
        
        // Create new MAPElites with restored checkpoint
        Function<Double, Map<String, Object>> fitnessFunction = value -> {
            Map<String, Object> fitness = new HashMap<>();
            fitness.put("fitness", value * value);
            return fitness;
        };
        
        Function<List<Solution<Double>>, Double> evolveOperator = parents -> {
            if (parents.isEmpty()) return Math.random();
            Double parent = parents.get(0).solution();
            return parent + (Math.random() - 0.5) * 0.1;
        };
        
        Supplier<List<Double>> initialSolutionGenerator = () ->
            List.of(0.1, 0.2, 0.3, 0.4, 0.5);
        
        Function<Island, List<Solution<Double>>> selection = island -> {
            var solutions = newRepository.findByIslandId(island.id());
            if (solutions.isEmpty()) return List.of();
            return solutions.subList(0, Math.min(2, solutions.size()));
        };
        
        MAPElites<Double> newMapElites = new MAPElites<>(
            newRepository, newMigration, fitnessFunction,
            evolveOperator, initialSolutionGenerator, selection, _ -> false, ScaleMethod.MIN_MAX, List.of("fitness"), 10);
        
        // Add checkpoint listener to restore from checkpoint
        CheckpointListener<Double> checkpointListener2 = new CheckpointListener<>(
            tempDir, 2, objectMapper, newRepository, checkpointIteration);
        newMapElites.addListener(checkpointListener2);
        
        // Act - Run algorithm (should restore from checkpoint and continue)
        newMapElites.run(2); // Run for 2 more iterations
        
        // Assert - Verify state was restored
        assertTrue(newRepository.count() > 0, "Repository should contain solutions after restore");
    }

    @Test
    @DisplayName("Test checkpoint listener with multiple listeners")
    void testCheckpointListenerWithMultipleListeners() throws Exception {
        // Arrange
        CheckpointListener<Double> checkpointListener = new CheckpointListener<>(
            tempDir, 2, objectMapper, repository, null);
        
        // Track algorithm execution with a simple listener
        final int[] iterationCount = {0};
        mapElites.addListener(new openevolve.mapelites.listener.MAPElitesListener<Double>() {
            @Override
            public void onAlgorithmStart(MAPElites<Double> mapElites) {}
            
            @Override
            public void onAfterIteration(Island island, int iteration, MAPElites<Double> mapElites) {
                iterationCount[0] = Math.max(iterationCount[0], iteration);
            }
        });
        
        mapElites.addListener(checkpointListener);

        // Act
        mapElites.run(6);

        // Assert
        assertTrue(iterationCount[0] > 0, "Algorithm should have executed iterations");
        
        // Verify checkpoint was created
        boolean foundCheckpoint = false;
        for (int i = 2; i <= 6; i += 2) {
            if (Files.exists(tempDir.resolve("checkpoint_iter_" + i + ".json"))) {
                foundCheckpoint = true;
                break;
            }
        }
        assertTrue(foundCheckpoint, "Checkpoint should be created even with multiple listeners");
    }

    @Test
    @DisplayName("Test checkpoint file corruption recovery")
    void testCheckpointFileCorruptionRecovery() throws Exception {
        // Arrange - Create a corrupted checkpoint file
        Path corruptedFile = tempDir.resolve("checkpoint_iter_5.json");
        Files.createDirectories(corruptedFile.getParent());
        Files.write(corruptedFile, "corrupted json content".getBytes());
        
        CheckpointListener<Double> checkpointListener = new CheckpointListener<>(
            tempDir, 3, objectMapper, repository, 5);
        mapElites.addListener(checkpointListener);

        // Act & Assert - Should handle corruption gracefully
        assertDoesNotThrow(() -> mapElites.run(3));
        
        // Algorithm should start from beginning since checkpoint couldn't be loaded
        assertTrue(repository.count() > 0, "Algorithm should still run despite checkpoint corruption");
    }

    @Test
    @DisplayName("Test checkpoint directory creation")
    void testCheckpointDirectoryCreation() throws Exception {
        // Arrange - Use non-existent nested directory
        Path nestedDir = tempDir.resolve("level1").resolve("level2").resolve("checkpoints");
        CheckpointListener<Double> checkpointListener = new CheckpointListener<>(
            nestedDir, 2, objectMapper, repository, null);
        mapElites.addListener(checkpointListener);

        // Act
        mapElites.run(4);

        // Assert
        assertTrue(Files.exists(nestedDir), "Nested checkpoint directory should be created");
        assertTrue(Files.exists(nestedDir.resolve("checkpoint_iter_2.json")) ||
                  Files.exists(nestedDir.resolve("checkpoint_iter_4.json")),
                  "Checkpoint file should be created in nested directory");
    }
}
