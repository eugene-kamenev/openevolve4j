package openevolve.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationContext;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import openevolve.EvolveSolution;
import openevolve.OpenEvolveConfig;
import openevolve.mapelites.Repository.Solution;
import openevolve.service.CommandService;
import openevolve.service.ConfigService;
import openevolve.service.OpenEvolveService;
import reactor.core.publisher.Mono;

import static reactor.function.TupleUtils.function;

public record Event<T extends Event.Payload>(String id, T payload) {

	// Output payloads
	public record Stopped() implements Output {
	}
	public record Started() implements Output {
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

	public record Solutions(String id, List<Solution<EvolveSolution>> solutions) implements Output {
	}

	public record OutputEvolutionEvent<T extends EvolutionEvent>(String taskId, T event)
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

		public record IterationDone(int iteration) implements EvolutionEvent {
		}
	}

	public static class GetSolutions extends Input<Solutions> {
		private String id;

		@Override
		public Mono<Solutions> get() {
			return getBean(ConfigService.class).map(s -> new Solutions(id, s.getSolutions(id)));
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}
	}

	// Input commands
	public static class Connect extends Input<Connected> {
		@Override
		public Mono<Connected> get() {
			return getBean(CommandService.class).map(c -> c.connect());
		}
	}
	public static class Start extends Input<Started> {
		private String id;
		private boolean restart;
		private List<UUID> initialSolutions;

		@Override
		public Mono<Started> get() {
			return Mono.zip(getBean(OpenEvolveService.class), getBean(ConfigService.class))
					.flatMap(function((openEvolveService, configService) -> {
						var solutions = configService.getSolutions(id);
						var solutionsById =
								solutions.stream().collect(Collectors.toMap(Solution::id, s -> s));
						var initial = new ArrayList<EvolveSolution>();
						if (initialSolutions != null && !initialSolutions.isEmpty()) {
							initial.addAll(solutions.stream()
									.filter(s -> initialSolutions.contains(s.id()))
									.map(s -> s.solution()).toList());
						}
						return openEvolveService.create(id, configService.findById(id), restart,
								initial, solutionsById).thenReturn(new Started());
					}));
		}

		public void setInitialSolutions(List<UUID> initialSolutions) {
			this.initialSolutions = initialSolutions;
		}

		public List<UUID> getInitialSolutions() {
			return initialSolutions;
		}

		public boolean getRestart() {
			return restart;
		}

		public void setRestart(boolean restart) {
			this.restart = restart;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}
	}

	public static class Stop extends Input<Stopped> {
		private String id;

		@Override
		public Mono<Stopped> get() {
			return getBean(OpenEvolveService.class)
					.flatMap(s -> s.stopProcess(id).thenReturn(new Stopped()));
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}
	}

	public static class CreateConfig extends Input<ConfigCreated> {
		private OpenEvolveConfig config;
		private String id;

		@Override
		public Mono<ConfigCreated> get() {
			return getBean(ConfigService.class).map(s -> s.save(config, id))
					.thenReturn(new ConfigCreated(id, config));
		}

		public OpenEvolveConfig getConfig() {
			return config;
		}

		public void setConfig(OpenEvolveConfig config) {
			this.config = config;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}
	}

	public static class UpdateConfig extends Input<ConfigUpdated> {
		private OpenEvolveConfig config;
		private String id;

		@Override
		public Mono<ConfigUpdated> get() {
			return getBean(ConfigService.class).map(s -> s.save(config, id))
					.thenReturn(new ConfigUpdated(id, config));
		}

		public OpenEvolveConfig getConfig() {
			return config;
		}

		public void setConfig(OpenEvolveConfig config) {
			this.config = config;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}
	}

	public static class DeleteConfig extends Input<ConfigDeleted> {
		private String id;

		@Override
		public Mono<ConfigDeleted> get() {
			return getBean(ConfigService.class).map(s -> s.delete(id))
					.thenReturn(new ConfigDeleted(id));
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}
	}

	@JsonSubTypes({@JsonSubTypes.Type(value = Event.Connect.class, name = "CONNECT"),
			@JsonSubTypes.Type(value = Event.Start.class, name = "START"),
			@JsonSubTypes.Type(value = Event.Stop.class, name = "STOP"),
			@JsonSubTypes.Type(value = Event.ConfigCreated.class, name = "CONFIG_CREATED"),
			@JsonSubTypes.Type(value = Event.ConfigUpdated.class, name = "CONFIG_UPDATED"),
			@JsonSubTypes.Type(value = Event.ConfigDeleted.class, name = "CONFIG_DELETED"),
			@JsonSubTypes.Type(value = Event.Connected.class, name = "CONNECTED"),
			@JsonSubTypes.Type(value = Event.CreateConfig.class, name = "CONFIG_CREATE"),
			@JsonSubTypes.Type(value = Event.UpdateConfig.class, name = "CONFIG_UPDATE"),
			@JsonSubTypes.Type(value = Event.DeleteConfig.class, name = "CONFIG_DELETE"),
			@JsonSubTypes.Type(value = Event.GetSolutions.class, name = "GET_SOLUTIONS"),
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
