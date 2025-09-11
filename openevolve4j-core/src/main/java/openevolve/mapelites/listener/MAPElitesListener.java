package openevolve.mapelites.listener;

import java.nio.file.Path;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import openevolve.mapelites.FeatureScaler;
import openevolve.mapelites.MAPElites;
import openevolve.mapelites.MAPElites.Cell;
import openevolve.mapelites.Repository.Island;
import openevolve.mapelites.Repository.RepositoryState;
import openevolve.mapelites.Repository.Solution;

/**
 * Listener interface for tracking various events during MAP-Elites algorithm execution.
 * Provides hooks for monitoring, logging, and extending algorithm behavior.
 */
public interface MAPElitesListener<T> extends Listener {

    /**
     * Called when the algorithm starts (before initialization).
     */
    default void onAlgorithmStart(MAPElites<T> mapElites) {
    }

    /**
     * Called before each iteration starts.
     * @param island the island that will be evolved this iteration
     * @param iteration current iteration number
     */
    default void onBeforeIteration(Island island, int iteration, MAPElites<T> mapElites) {
    }

    /**
     * Called after an iteration completes.
     * @param island the island that was evolved
     * @param iteration iteration number that just completed
     */
    default void onAfterIteration(Island island, int iteration, MAPElites<T> mapElites) {
    }

    /**
     * Called when a solution is selected for evolution.
     * @param selectedSolutions solutions chosen for evolution
     * @param island island they were selected from
     * @param iteration current iteration
     */
    default void onSolutionSelection(java.util.List<Solution<T>> selectedSolutions, Island island, int iteration) {
    }

    /**
     * Called when a new solution is generated through evolution.
     * @param newSolution the newly generated solution
     * @param parents parent solutions used to generate this solution
     * @param iteration current iteration
     */
    default void onSolutionGenerated(Solution<T> newSolution, java.util.List<Solution<T>> parents, int iteration) {
    }

    /**
     * Called when a new best solution is found (globally across all islands).
     * @param newBest the new best solution
     * @param previousBest the previous best solution (null if this is the first)
     * @param iteration current iteration
     */
    default void onNewBestSolution(Solution<T> newBest, Solution<T> previousBest, int iteration) {
    }

    /**
     * Called when an error occurs during algorithm execution.
     * @param error the exception that occurred
     * @param context description of what was happening when error occurred
     * @param iteration current iteration
     */
    default void onError(Exception error, String context, int iteration, MAPElites<T> mapElites) {
    }

    /**
     * Called when a solution successfully improves a cell (replaces existing solution or fills empty cell).
     * @param newSolution the solution that improved the cell
     * @param previousSolution the solution that was replaced (null if cell was empty)
     * @param cellCoordinates coordinates of the improved cell
     * @param iteration current iteration
     */
    default void onCellImproved(Solution<T> newSolution, Solution<T> previousSolution, Cell cell, int iteration) {
    }

    /**
     * Called when a solution fails to improve a cell (dominated by existing solution).
     * @param candidateSolution the solution that failed to improve the cell
     * @param existingSolution the existing solution that dominated the candidate
     * @param cellCoordinates coordinates of the cell
     * @param iteration current iteration
     */
    default void onCellRejected(Solution<T> candidateSolution, Solution<T> existingSolution, Cell cell, int iteration) {
    }

    public static class StateWriter<T> implements MAPElitesListener<T> {
        private final Path statePath;
        private final ObjectMapper mapper;

        public StateWriter(Path statePath, ObjectMapper mapper) {
            this.statePath = statePath;
            this.mapper = mapper;
        }

        @Override
        public void onAfterIteration(Island island, int iteration, MAPElites<T> mapElites) {
            State<T> state = new State<>(iteration, mapElites.getRepositoryState(), mapElites.getGrid(), mapElites.getFeatureStats());
            try {
                mapper.writeValue(statePath.toFile(), state);
            } catch (Exception e) {
                System.err.println("Warning: Failed to write MAP-Elites state to " + statePath);
                e.printStackTrace();
            }
        }

        public record State<T>(int iteration, RepositoryState repository, Map<String, Cell> grid, Map<String, FeatureScaler> featureStats) {
        }
    }
}
