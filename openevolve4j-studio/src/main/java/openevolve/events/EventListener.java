package openevolve.events;

import java.util.UUID;
import openevolve.EvolveSolution;
import openevolve.events.Event.EvolutionEvent;
import openevolve.events.Event.OutputEvolutionEvent;
import openevolve.mapelites.MAPElites;
import openevolve.mapelites.MAPElites.Cell;
import openevolve.mapelites.Repository.Island;
import openevolve.mapelites.Repository.Solution;
import openevolve.mapelites.listener.MAPElitesListener;
import openevolve.mapelites.listener.RepositoryListener;
import openevolve.service.EventBus;

public class EventListener
		implements MAPElitesListener<EvolveSolution>, RepositoryListener<EvolveSolution> {
	private final EventBus bus;
	private final String taskId;

	public EventListener(EventBus eventService, String taskId) {
		this.bus = eventService;
		this.taskId = taskId;
	}

	@Override
	public void onSolutionAdded(Solution<EvolveSolution> solution) {
		bus.outputSink.tryEmitNext(new Event<>(UUID.randomUUID().toString(),
				new OutputEvolutionEvent<>(taskId, new EvolutionEvent.SolutionAdded(solution)))).orThrow();
	}

	@Override
	public void onSolutionRemoved(Solution<EvolveSolution> solution) {
		bus.outputSink.tryEmitNext(new Event<>(UUID.randomUUID().toString(),
				new OutputEvolutionEvent<>(taskId, new EvolutionEvent.SolutionRemoved(solution)))).orThrow();
	}

	@Override
	public void onCellImproved(Solution<EvolveSolution> newSolution,
			Solution<EvolveSolution> previousSolution, Cell cell, int iteration) {
		bus.outputSink.tryEmitNext(new Event<>(UUID.randomUUID().toString(), new OutputEvolutionEvent<>(taskId,
				new EvolutionEvent.CellImproved(newSolution, previousSolution, iteration)))).orThrow();
	}

	@Override
	public void onCellRejected(Solution<EvolveSolution> candidateSolution,
			Solution<EvolveSolution> existingSolution, Cell cell, int iteration) {
		bus.outputSink.tryEmitNext(new Event<>(UUID.randomUUID().toString(), new OutputEvolutionEvent<>(taskId,
				new EvolutionEvent.CellRejected(candidateSolution, existingSolution, iteration)))).orThrow();
	}

	@Override
	public void onNewBestSolution(Solution<EvolveSolution> newBest,
			Solution<EvolveSolution> previousBest, int iteration) {
		bus.outputSink.tryEmitNext(new Event<>(UUID.randomUUID().toString(), new OutputEvolutionEvent<>(taskId,
				new EvolutionEvent.NewBestSolution(newBest, previousBest, iteration)))).orThrow();
	}

	@Override
	public void onError(Exception error, String context, int iteration, MAPElites<EvolveSolution> mapElites) {
		bus.outputSink.tryEmitNext(new Event<>(UUID.randomUUID().toString(), new OutputEvolutionEvent<>(taskId,
				new EvolutionEvent.Error(error.getMessage(), context, iteration)))).orThrow();
	}

	@Override
	public void onAfterIteration(Island island, int iteration,
			MAPElites<EvolveSolution> mapElites) {
		bus.outputSink.tryEmitNext(new Event<>(UUID.randomUUID().toString(),
				new OutputEvolutionEvent<>(taskId, new EvolutionEvent.IterationDone(iteration)))).orThrow();
	}
}
