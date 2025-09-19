package openevolve.events;

import java.util.UUID;
import openevolve.EvolveSolution;
import openevolve.events.Event.EvolutionEvent;
import openevolve.events.Event.OutputEvolutionEvent;
import openevolve.mapelites.MAPElites;
import openevolve.mapelites.MAPElites.Cell;
import openevolve.mapelites.MAPElites.State;
import openevolve.mapelites.Population.Island;
import openevolve.mapelites.Population.Solution;
import openevolve.mapelites.listener.MAPElitesListener;
import openevolve.mapelites.listener.RepositoryListener;
import openevolve.service.EventBus;

public class EventListener
		implements MAPElitesListener<EvolveSolution>, RepositoryListener<EvolveSolution> {

	private final EventBus bus;
	private final UUID taskId;
	private UUID runId;

	public EventListener(EventBus eventService, UUID evolutionProblemId) {
		this.bus = eventService;
		this.taskId = evolutionProblemId;
	}

	@Override
	public void onAlgorithmStart(MAPElites<EvolveSolution> mapElites) {
		runId = mapElites.getRunId();
	}

	@Override
	public void onSolutionAdded(Solution<EvolveSolution> solution) {
		emitEvent(new EvolutionEvent.SolutionAdded(solution));
	}

	@Override
	public void onSolutionRemoved(Solution<EvolveSolution> solution) {
		emitEvent(new EvolutionEvent.SolutionRemoved(solution));
	}

	@Override
	public void onCellImproved(Solution<EvolveSolution> newSolution,
			Solution<EvolveSolution> previousSolution, Cell cell, int iteration) {
		emitEvent(new EvolutionEvent.CellImproved(newSolution, previousSolution, iteration));
	}

	@Override
	public void onCellRejected(Solution<EvolveSolution> candidateSolution,
			Solution<EvolveSolution> existingSolution, Cell cell, int iteration) {
		emitEvent(new EvolutionEvent.CellRejected(candidateSolution, existingSolution, iteration));
	}

	@Override
	public void onNewBestSolution(Solution<EvolveSolution> newBest,
			Solution<EvolveSolution> previousBest, int iteration) {
		emitEvent(new EvolutionEvent.NewBestSolution(newBest, previousBest, iteration));
	}

	@Override
	public void onError(Exception error, String context, int iteration,
			MAPElites<EvolveSolution> mapElites) {
		emitEvent(new EvolutionEvent.Error(error.getMessage(), context, iteration));
	}

	@Override
	public void onAfterIteration(Island island, int iteration,
			MAPElites<EvolveSolution> mapElites) {
		var populationState = mapElites.getRepositoryState();
		var state = new State(runId, iteration, populationState, mapElites.getGrid(), mapElites.getFeatureStats());
		emitEvent(new EvolutionEvent.IterationDone(iteration, state));
	}

	private <T extends EvolutionEvent> void emitEvent(T event) {
		bus.outputSink.tryEmitNext(new Event<>(UUID.randomUUID().toString(),
				new OutputEvolutionEvent<>(taskId, runId, event))).orThrow();
	}
}
