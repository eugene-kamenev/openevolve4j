package openevolve.events;

import java.util.Map;
import java.util.function.Supplier;
import org.springframework.context.ApplicationContext;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import openevolve.OpenEvolveConfig;
import openevolve.service.ConfigService;
import openevolve.service.OpenEvolveService;
import reactor.core.publisher.Mono;

import static reactor.function.TupleUtils.function;

public record Event<T extends Event.Payload>(String id, T payload) {

	// Output payloads
	public record Stopped(String problem) implements Output {
	}
	public record Started(String problem) implements Output {
	}
	public record ConfigCreated(String id, OpenEvolveConfig config) implements Output {
	}
	public record ConfigUpdated(String id, OpenEvolveConfig config) implements Output {
	}
	public record ConfigDeleted(String id) implements Output {
	}
	public record Connected(Map<String, OpenEvolveConfig> existing) implements Output {
	}

	// Input commands
	public static class Connect extends Input<Connected> {
		@Override
		public Mono<Connected> get() {
			return getBean(ConfigService.class).map(s -> new Connected(s.reload()));
		}
	}

	public static class Start extends Input<Started> {
		private String id;

		@Override
		public Mono<Started> get() {
			return Mono.zip(getBean(OpenEvolveService.class), getBean(ConfigService.class))
					.flatMap(function((openEvolveService, configService) -> openEvolveService
							.create(id, configService.findById(id)).thenReturn(new Started(id))));
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
					.flatMap(s -> s.stopProcess(id).thenReturn(new Stopped(id)));
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
			return getBean(ConfigService.class)
				.map(s -> s.save(config, id))
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
			return getBean(ConfigService.class)
				.map(s -> s.save(config, id))
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
			return getBean(ConfigService.class)
				.map(s -> s.delete(id))
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
			@JsonSubTypes.Type(value = Event.DeleteConfig.class, name = "CONFIG_DELETE")})
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
