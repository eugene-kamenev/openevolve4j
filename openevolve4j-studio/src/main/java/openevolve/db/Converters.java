package openevolve.db;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import openevolve.EvolveSolution;
import openevolve.OpenEvolveConfig;
import openevolve.BaseAgent.Change;
import openevolve.mapelites.MAPElites.State;
import openevolve.mapelites.Population.MAPElitesMetadata;

@Configuration
public class Converters {

	@Bean
	public R2dbcCustomConversions r2dbcCustomConversions(ObjectMapper mapper) {
		return R2dbcCustomConversions.of(PostgresDialect.INSTANCE,
				List.of(
						new EvolveSolutionReader(mapper),
						new EvolveSolutionWriter(mapper),
						new MetadataReader(mapper), new MetadataWriter(mapper),
						new EvolutionConfigReader(mapper),
						new EvolutionConfigWriter(mapper),
						new StateReader(mapper), new StateWriter(mapper), new MapReader(mapper),
						new MapWriter(mapper), new ChangesReader(mapper), new ChangesWriter(mapper),
						new MapWithPathKeyToStringReader(mapper),
						new MapWithPathKeyToStringWriter(mapper)));
	}

	public static class MetadataReader extends JsonReaderConverter<MAPElitesMetadata> {
		public MetadataReader(ObjectMapper mapper) {
			super(mapper, new TypeReference<>() {});
		}
	}

	public static class MetadataWriter extends JsonWriterConverter<MAPElitesMetadata> {
		public MetadataWriter(ObjectMapper mapper) {
			super(mapper);
		}
	}

	public static class EvolutionConfigReader extends JsonReaderConverter<OpenEvolveConfig> {
		public EvolutionConfigReader(ObjectMapper mapper) {
			super(mapper, new TypeReference<>() {});
		}
	}

	public static class EvolutionConfigWriter extends JsonWriterConverter<OpenEvolveConfig> {
		public EvolutionConfigWriter(ObjectMapper mapper) {
			super(mapper);
		}
	}

	public static class EvolveSolutionReader extends JsonReaderConverter<EvolveSolution> {
		public EvolveSolutionReader(ObjectMapper mapper) {
			super(mapper, new TypeReference<>() {});
		}
	}

	public static class EvolveSolutionWriter extends JsonWriterConverter<EvolveSolution> {
		public EvolveSolutionWriter(ObjectMapper mapper) {
			super(mapper);
		}
	}

	public static class StateReader extends JsonReaderConverter<State> {
		public StateReader(ObjectMapper mapper) {
			super(mapper, new TypeReference<>() {});
		}
	}

	public static class StateWriter extends JsonWriterConverter<State> {
		public StateWriter(ObjectMapper mapper) {
			super(mapper);
		}
	}

	public static class MapReader extends JsonReaderConverter<Map<String, Object>> {
		public MapReader(ObjectMapper mapper) {
			super(mapper, new TypeReference<>() {});
		}
	}

	public static class MapWriter extends JsonWriterConverter<Map<String, Object>> {
		public MapWriter(ObjectMapper mapper) {
			super(mapper);
		}
	}

	public static class ChangesReader extends JsonReaderConverter<Map<Path, List<Change>>> {
		public ChangesReader(ObjectMapper mapper) {
			super(mapper, new TypeReference<>() {});
		}
	}

	public static class ChangesWriter extends JsonWriterConverter<Map<Path, List<Change>>> {
		public ChangesWriter(ObjectMapper mapper) {
			super(mapper);
		}
	}

	public static class MapWithPathKeyToStringReader
			extends JsonReaderConverter<Map<Path, String>> {
		public MapWithPathKeyToStringReader(ObjectMapper mapper) {
			super(mapper, new TypeReference<>() {});
		}
	}

	public static class MapWithPathKeyToStringWriter
			extends JsonWriterConverter<Map<Path, String>> {
		public MapWithPathKeyToStringWriter(ObjectMapper mapper) {
			super(mapper);
		}
	}

	@ReadingConverter
	private static abstract class JsonReaderConverter<T> implements Converter<Json, T> {
		private final ObjectMapper mapper;
		private final TypeReference<T> typeRef;

		protected JsonReaderConverter(ObjectMapper mapper, TypeReference<T> typeRef) {
			this.mapper = mapper;
			this.typeRef = typeRef;
		}

		@Override
		public T convert(Json json) {
			if (json == null) {
				return null;
			}
			String source = json.asString();
			try {
				return mapper.readValue(source, typeRef);
			} catch (JsonProcessingException e) {
				throw new RuntimeException("Failed to convert JSON string to object", e);
			}
		}
	}

	@WritingConverter
	private static abstract class JsonWriterConverter<T> implements Converter<T, Json> {
		private final ObjectMapper mapper;

		protected JsonWriterConverter(ObjectMapper mapper) {
			this.mapper = mapper;
		}

		@Override
		public Json convert(T source) {
			if (source == null) {
				return null;
			}
			try {
				return Json.of(mapper.writeValueAsString(source));
			} catch (JsonProcessingException e) {
				throw new RuntimeException("Failed to convert object to JSON string", e);
			}
		}
	}
}
