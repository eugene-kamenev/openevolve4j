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
import openevolve.domain.PuctTreeConfig;
import openevolve.studio.domain.Event.Payload;

@Configuration
public class Converters {

	@Bean
	public R2dbcCustomConversions r2dbcCustomConversions(ObjectMapper mapper) {
		return R2dbcCustomConversions.of(PostgresDialect.INSTANCE,
				List.of(
						new EvolutionConfigReader(mapper),
						new EvolutionConfigWriter(mapper),
						new MapReader(mapper),
						new MapWriter(mapper),
						new MapWithPathKeyToStringReader(mapper),
						new MapWithPathKeyToStringWriter(mapper),
						new SolutionReader(mapper),
						new SolutionWriter(mapper))
		);
	}

	public static class SolutionReader extends JsonReaderConverter<Payload> {
		public SolutionReader(ObjectMapper mapper) {
			super(mapper, new TypeReference<>() {});
		}
	}

	public static class SolutionWriter extends JsonWriterConverter<Payload> {
		public SolutionWriter(ObjectMapper mapper) {
			super(mapper);
		}
	}

	public static class SourceTreeReader extends JsonReaderConverter<openevolve.domain.SourceTree> {
		public SourceTreeReader(ObjectMapper mapper) {
			super(mapper, new TypeReference<>() {});
		}
	}

	public static class SourceTreeWriter extends JsonWriterConverter<openevolve.domain.SourceTree> {
		public SourceTreeWriter(ObjectMapper mapper) {
			super(mapper);
		}
	}

	public static class EvolutionConfigReader extends JsonReaderConverter<PuctTreeConfig> {
		public EvolutionConfigReader(ObjectMapper mapper) {
			super(mapper, new TypeReference<>() {});
		}
	}

	public static class EvolutionConfigWriter extends JsonWriterConverter<PuctTreeConfig> {
		public EvolutionConfigWriter(ObjectMapper mapper) {
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
