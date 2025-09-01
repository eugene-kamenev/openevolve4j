package openevolve.mapelites.listener;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import openevolve.mapelites.MAPElites;
import openevolve.mapelites.MAPElites.Cell;
import openevolve.mapelites.Repository.Island;
import openevolve.mapelites.Repository.Solution;

public class MAPElitesLoggingListener<T> implements MAPElitesListener<T> {

	private static final Logger LOG = LoggerFactory.getLogger(MAPElitesLoggingListener.class);

	@Override
	public void onAfterIteration(Island island, int iteration, MAPElites<T> mapElites) {
		if (LOG.isDebugEnabled()) {
			LOG.debug("After iteration {}: {}", iteration, island);
		}
	}

	@Override
	public void onAlgorithmStart(MAPElites<T> mapElites) {
		if (LOG.isInfoEnabled()) {
			LOG.info("Algorithm started");
		}
	}

	@Override
	public void onBeforeIteration(Island island, int iteration, MAPElites<T> mapElites) {
		if (LOG.isDebugEnabled()) {
			LOG.debug("Before iteration {}: {}", iteration, island);
		}
	}

	@Override
	public void onError(Exception error, String context, int iteration, MAPElites<T> mapElites) {
		if (LOG.isErrorEnabled()) {
			LOG.error("Error occurred at iteration {}: {}", iteration, context, error);
		}
	}

	@Override
	public void onNewBestSolution(Solution<T> newBest, Solution<T> previousBest, int iteration) {
		if (LOG.isInfoEnabled()) {
			LOG.info("🏆 New best solution found at iteration {}: {}", iteration, newBest);
		}
	}

	@Override
	public void onSolutionGenerated(Solution<T> newSolution, List<Solution<T>> parents,
			int iteration) {
		if (LOG.isDebugEnabled()) {
			LOG.debug("✨ Solution generated at iteration {}: {}", iteration, newSolution);
		}
	}

	@Override
	public void onSolutionSelection(List<Solution<T>> selectedSolutions, Island island,
			int iteration) {
		if (LOG.isDebugEnabled()) {
			LOG.debug("Solutions selected at iteration {}: {}", iteration, selectedSolutions);
		}
	}

	@Override
	public void onCellImproved(Solution<T> newSolution, Solution<T> previousSolution, Cell cell,
			int iteration) {
		if (LOG.isInfoEnabled()) {
			LOG.info("🎉 Cell: {} improved at iteration {}: {} -> {}", cell, iteration, previousSolution, newSolution);
		}
	}

	@Override
	public void onCellRejected(Solution<T> candidateSolution, Solution<T> existingSolution,
			Cell cell, int iteration) {
		if (LOG.isInfoEnabled()) {
			LOG.info("❌ Cell: {} rejected at iteration {}: {} -> {}", cell, iteration, existingSolution, candidateSolution);
		}
	}

}
