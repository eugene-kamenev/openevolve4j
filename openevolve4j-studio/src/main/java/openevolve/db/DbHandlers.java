package openevolve.db;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.NullHandling;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.domain.SqlSort.SqlOrder;
import org.springframework.stereotype.Component;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import openevolve.studio.domain.Problem;
import openevolve.studio.domain.Event.Payload;
import openevolve.studio.domain.Event;
import openevolve.studio.domain.LLMModel;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Configuration
public class DbHandlers {

	@Component
	public static final class LLMModelDbHandler extends DbHandler<LLMModel> {

		public LLMModelDbHandler(R2dbcEntityTemplate tmpl) {
			super(LLMModel.class, tmpl, Map.of(), _ -> null);
		}

		public Mono<Void> syncModels(Collection<String> modelNames) {
			return Flux.fromIterable(modelNames).flatMap(name -> tmpl.getDatabaseClient().sql("""
					INSERT INTO llm_model (id, name)
						SELECT gen_random_uuid(), :name
						WHERE NOT EXISTS (
							SELECT 1 FROM llm_model WHERE name = :name
						)
					""").bind("name", name).then()).then();

		}
	}

	@Component
	public static final class EventDbHandler extends DbHandler<Event<?>> {

		private static final Map<String, Function<Object, Criteria>> ENTITY_FILTERS = Map.of(
				"forId", val -> Criteria.where("payload->>'id'").is(val.toString()), "forRun",
				val -> Criteria.where("payload->>'runId'").is(val.toString()), "forProblem",
				val -> Criteria.where("problem_id").is(UUID.fromString(val.toString())), "forType",
				val -> Criteria.where("payload->>'type'").is(val.toString()), "forModel",
				val -> Criteria.where("payload->'data'->'metadata'->>'llmModel'")
						.is(val.toString()),
				"forParent", val -> Criteria.where("payload->>'parentId'").is(val.toString()));

		@SuppressWarnings("unchecked")
		public EventDbHandler(R2dbcEntityTemplate tmpl) {
			super((Class<Event<?>>) (Class<?>) Event.class, tmpl, ENTITY_FILTERS, _ -> null);
		}

		@SuppressWarnings("unchecked")
		public <S extends Payload> Flux<S> cast(Flux<? extends Event<?>> events) {
			return events.map(ev -> (S) ev.payload());
		}

		@SuppressWarnings("unchecked")
		public <S extends Payload> Mono<S> cast(Mono<? extends Event<?>> event) {
			return event.map(ev -> (S) ev.payload());
		}

		public Flux<Event.Solution<?>> ancestors(UUID problemId, UUID id) {
			return cast(tmpl.getDatabaseClient().sql("""
					WITH RECURSIVE ancestors AS (
					    -- start from the given node
					    SELECT *
					    FROM events
					    WHERE payload->>'type' = 'SOLUTION' AND payload->>'id' = :id AND problem_id = :problemId

					    UNION ALL

					    SELECT i.*
					    FROM events i
					    	JOIN ancestors a
					        ON i.payload->>'id' = a.payload->>'parentId'
						WHERE i.payload->>'type' = 'SOLUTION' AND i.problem_id = :problemId
					)
					SELECT * FROM ancestors WHERE payload->>'id' <> :id ORDER BY date_created DESC;
										""").bind("id", id.toString()).bind("problemId", problemId)
										.map((Row r, RowMetadata m) -> tmpl.getConverter().read(entityClass, r, m))
										.all());

		}

		public static Sort fitnessSort(Map<String, Boolean> metrics) {
			Sort sort = Sort.unsorted();
			for (var e : metrics.entrySet()) {
				String defaultLiteral = e.getValue() ? "'-Infinity'::float8" : "'Infinity'::float8";
				var key = "COALESCE(CAST(NULLIF(payload->'fitness'->>'" + e.getKey()
						+ "', 'NaN') as float8)," + defaultLiteral + ")";
				var order = SqlOrder.by(key)
						.with(e.getValue() ? Sort.Direction.DESC : Sort.Direction.ASC)
						.with(NullHandling.NULLS_LAST).withUnsafe();
				sort = sort.and(Sort.by(order));
			}
			return sort;
		}
	}

	@Component
	public static final class ProblemHandler extends DbHandler<Problem> {
		public ProblemHandler(R2dbcEntityTemplate tmpl) {
			super(Problem.class, tmpl, Map.of(), _ -> null);
		}
	}
}
