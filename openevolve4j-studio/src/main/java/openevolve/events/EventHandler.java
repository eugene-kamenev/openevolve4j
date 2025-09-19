package openevolve.events;

import java.time.Instant;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import openevolve.db.DbHandler;
import openevolve.db.EvolutionSolution;
import openevolve.db.EvolutionState;
import openevolve.events.Event.EvolutionEvent;
import openevolve.events.Event.OutputEvolutionEvent;
import openevolve.events.Event.EvolutionEvent.IterationDone;
import openevolve.events.Event.EvolutionEvent.SolutionAdded;
import openevolve.service.EventBus;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class EventHandler implements DisposableBean {
	private final Disposable subscription;

	public EventHandler(EventBus eventBus, DbHandler<EvolutionSolution> solutionHandler,
			DbHandler<EvolutionState> stateHandler) {
		this.subscription =
				eventBus.outputEventStream.filter(this::matches).flatMap((Event<?> e) -> {
					var outputEvent = toOutputEvolutionEvent(e);
					if (outputEvent.event() instanceof SolutionAdded added) {
						return solutionHandler
								.save(EvolutionSolution.fromPopulationSolution(added.solution(), outputEvent.evolutionProblemId()));
					} else if (outputEvent.event() instanceof IterationDone done) {
						var updatedState = done.state();
						var create = stateHandler.save(new EvolutionState(outputEvent.runId(),
								Instant.now(), updatedState));
						var update = stateHandler.findById(outputEvent.runId()).flatMap(
								state -> stateHandler.update(new EvolutionState(outputEvent.runId(),
										state.dateCreated(), updatedState)));
						return update.switchIfEmpty(create);
					}
					return Mono.empty();
				}).subscribeOn(Schedulers.boundedElastic()).subscribe();
	}

	private boolean matches(Event<?> event) {
		return event.payload() instanceof OutputEvolutionEvent<?> evt
				&& (evt.event() instanceof EvolutionEvent.SolutionAdded
						|| evt.event() instanceof IterationDone);
	}

	private <T extends EvolutionEvent> OutputEvolutionEvent<?> toOutputEvolutionEvent(
			Event<?> event) {
		return (OutputEvolutionEvent<?>) event.payload();
	}

	@Override
	public void destroy() throws Exception {
		subscription.dispose();
	}
}
