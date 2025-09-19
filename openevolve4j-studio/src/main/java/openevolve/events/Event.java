package openevolve.events;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.context.ApplicationContext;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import openevolve.EvolveSolution;
import openevolve.OpenEvolveConfig;
import openevolve.mapelites.MAPElites.State;
import openevolve.mapelites.Population.Solution;
import reactor.core.publisher.Mono;

public record Event<T extends Event.Payload>(String id, T payload) {

	// Output payloads
	public record Stopped(String id) implements Output {
	}
	public record Started(String id) implements Output {
	}
	public record ConfigCreated(String id, OpenEvolveConfig config) implements Output {
	}
	public record ConfigUpdated(String id, OpenEvolveConfig config) implements Output {
	}
	public record ConfigDeleted(String id) implements Output {
	}
	public record Connected(Map<String, OpenEvolveConfig> existing, Map<String, String> statuses)
			implements Output {
	}

	public record Solutions(String id, List<Solution<EvolveSolution>> solutions, UUID bestId) implements Output {
	}

	public record OutputEvolutionEvent<T extends EvolutionEvent>(UUID evolutionProblemId, UUID runId, T event)
			implements Output {
	}

	@JsonSubTypes({
			@JsonSubTypes.Type(value = EvolutionEvent.SolutionAdded.class, name = "SOLUTION_ADDED"),
			@JsonSubTypes.Type(value = EvolutionEvent.SolutionRemoved.class,
					name = "SOLUTION_REMOVED"),
			@JsonSubTypes.Type(value = EvolutionEvent.CellImproved.class, name = "CELL_IMPROVED"),
			@JsonSubTypes.Type(value = EvolutionEvent.CellRejected.class, name = "CELL_REJECTED"),
			@JsonSubTypes.Type(value = EvolutionEvent.NewBestSolution.class,
					name = "NEW_BEST_SOLUTION"),
			@JsonSubTypes.Type(value = EvolutionEvent.Error.class, name = "ERROR"),
			@JsonSubTypes.Type(value = EvolutionEvent.IterationDone.class,
					name = "ITERATION_DONE")})
	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
	public interface EvolutionEvent extends Output {
		public record SolutionAdded(Solution<EvolveSolution> solution) implements EvolutionEvent {
		}
		public record SolutionRemoved(Solution<EvolveSolution> solution) implements EvolutionEvent {
		}
		public record CellImproved(Solution<EvolveSolution> newSolution,
				Solution<EvolveSolution> previousSolution, int iteration)
				implements EvolutionEvent {
		}
		public record CellRejected(Solution<EvolveSolution> candidateSolution,
				Solution<EvolveSolution> existingSolution, int iteration)
				implements EvolutionEvent {
		}
		public record NewBestSolution(Solution<EvolveSolution> newBest,
				Solution<EvolveSolution> previousBest, int iteration) implements EvolutionEvent {
		}

		public record Error(String exceptionMessage, String context, int iteration)
				implements EvolutionEvent {
		}

		public record IterationDone(int iteration, State state) implements EvolutionEvent {
		}
	}

	@JsonSubTypes({
			@JsonSubTypes.Type(value = Event.ConfigCreated.class, name = "CONFIG_CREATED"),
			@JsonSubTypes.Type(value = Event.ConfigUpdated.class, name = "CONFIG_UPDATED"),
			@JsonSubTypes.Type(value = Event.ConfigDeleted.class, name = "CONFIG_DELETED"),
			@JsonSubTypes.Type(value = Event.Connected.class, name = "CONNECTED"),
			@JsonSubTypes.Type(value = Event.Solutions.class, name = "SOLUTIONS"),
			@JsonSubTypes.Type(value = Event.Started.class, name = "STARTED"),
			@JsonSubTypes.Type(value = Event.Stopped.class, name = "STOPPED"),
			@JsonSubTypes.Type(value = Event.OutputEvolutionEvent.class, name = "EVOLUTION_EVENT")})
	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
	public static interface Payload {
	}

	public static abstract class Input<T extends Output> implements Payload, Supplier<Mono<T>> {

		public static final String APP_CTX_KEY = "appContext";

		public final Mono<ApplicationContext> applicationContext$ =
				Mono.deferContextual(ctx -> Mono.just(ctx.get(APP_CTX_KEY)));

		public <S> Mono<S> getBean(Class<S> clazz) {
			return applicationContext$.map(ctx -> ctx.getBean(clazz));
		}
	}

	public static interface Output extends Payload {
	}
}
