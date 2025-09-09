package openevolve.events;

import java.util.List;
import openevolve.EvolveSolution;
import openevolve.mapelites.MAPElites;
import openevolve.mapelites.MAPElites.Cell;
import openevolve.mapelites.Repository.Island;
import openevolve.mapelites.Repository.Solution;
import openevolve.mapelites.listener.MAPElitesListener;
import openevolve.service.EventService;

public class EventListener implements MAPElitesListener<EvolveSolution> {
	private final EventService eventService;
	private final String taskId;

	public EventListener(EventService eventService, String taskId) {
		this.eventService = eventService;
		this.taskId = taskId;
	}

	@Override
	public void onAfterIteration(Island island, int iteration, MAPElites<EvolveSolution> mapElites) {
		MAPElitesListener.super.onAfterIteration(island, iteration, mapElites);
	}

	@Override
	public void onAlgorithmStart(MAPElites<EvolveSolution> mapElites) {
		MAPElitesListener.super.onAlgorithmStart(mapElites);
	}

	@Override
	public void onBeforeIteration(Island island, int iteration, MAPElites<EvolveSolution> mapElites) {
		MAPElitesListener.super.onBeforeIteration(island, iteration, mapElites);
	}

	@Override
	public void onCellImproved(Solution<EvolveSolution> newSolution,
			Solution<EvolveSolution> previousSolution, Cell cell, int iteration) {
		MAPElitesListener.super.onCellImproved(newSolution, previousSolution, cell, iteration);
	}

	@Override
	public void onCellRejected(Solution<EvolveSolution> candidateSolution,
			Solution<EvolveSolution> existingSolution, Cell cell, int iteration) {
		MAPElitesListener.super.onCellRejected(candidateSolution, existingSolution, cell, iteration);
	}

	@Override
	public void onError(Exception error, String context, int iteration,
			MAPElites<EvolveSolution> mapElites) {
		MAPElitesListener.super.onError(error, context, iteration, mapElites);
	}

	@Override
	public void onNewBestSolution(Solution<EvolveSolution> newBest,
			Solution<EvolveSolution> previousBest, int iteration) {
		MAPElitesListener.super.onNewBestSolution(newBest, previousBest, iteration);
	}

	@Override
	public void onSolutionGenerated(Solution<EvolveSolution> newSolution,
			List<Solution<EvolveSolution>> parents, int iteration) {
		MAPElitesListener.super.onSolutionGenerated(newSolution, parents, iteration);
	}

	@Override
	public void onSolutionSelection(List<Solution<EvolveSolution>> selectedSolutions, Island island,
			int iteration) {
		MAPElitesListener.super.onSolutionSelection(selectedSolutions, island, iteration);
	}
}
