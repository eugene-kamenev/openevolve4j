package openevolve.db;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Configuration
public class DbHandlers {

	@Component
	public static final class LLMModelDbHandler extends DbHandler<LLMModel> {

		private static final Map<String, Function<Object, Criteria>> ENTITY_FILTERS =
				Map.of("byName", val -> Criteria.where("name").is(val));

		public LLMModelDbHandler(R2dbcEntityTemplate tmpl) {
			super(LLMModel.class, tmpl, ENTITY_FILTERS, _ -> null);
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
	public static final class EvolutionStateDbHandler extends DbHandler<EvolutionState> {

		private static final Map<String, Function<Object, Criteria>> ENTITY_FILTERS = Map
				.of("forRun", val -> Criteria.where("run_id").is(UUID.fromString(val.toString())));

		public EvolutionStateDbHandler(R2dbcEntityTemplate tmpl) {
			super(EvolutionState.class, tmpl, ENTITY_FILTERS, _ -> null);
		}
	}

	@Component
	public static final class EvolutionSolutionDbHandler extends DbHandler<EvolutionSolution> {

		private static final Map<String, Function<Object, Criteria>> ENTITY_FILTERS = Map.of(
				"forParent", val -> Criteria.where("parent_id").is(UUID.fromString(val.toString())),
				"forRun", val -> Criteria.where("run_id").is(UUID.fromString(val.toString())),
				"forIds", val -> Criteria.where("id").in((Collection<?>) val), "forLLMModel",
				val -> Criteria.where("solution->'metadata'->>'llmModel'").is(val.toString()));

		public EvolutionSolutionDbHandler(R2dbcEntityTemplate tmpl) {
			super(EvolutionSolution.class, tmpl, ENTITY_FILTERS, _ -> null);
		}

		public Flux<EvolutionSolution> findAllParents(UUID childId) {
			return tmpl.getDatabaseClient().sql("""
					WITH RECURSIVE parent_solutions AS (
						SELECT * FROM solution WHERE id = :childId
						UNION ALL
						SELECT s.* FROM solution s
						INNER JOIN parent_solutions ps ON s.id = ps.parent_id
					)
					SELECT * FROM parent_solutions;
					""").bind("childId", childId)
					.map((r, _) -> tmpl.getConverter().read(EvolutionSolution.class, r)).all();
		}
	}

	@Component
	public static final class EvolutionRunDbHandler extends DbHandler<EvolutionRun> {

		private static final Map<String, Function<Object, Criteria>> ENTITY_FILTERS =
				Map.of("forProblem",
						val -> Criteria.where("problem_id").is(UUID.fromString(val.toString())));

		public EvolutionRunDbHandler(R2dbcEntityTemplate tmpl) {
			super(EvolutionRun.class, tmpl, ENTITY_FILTERS, _ -> null);
		}
	}

	@Component
	public static final class EvolutionProblemDbHandler extends DbHandler<EvolutionProblem> {

		private static final Map<String, Function<Object, Criteria>> ENTITY_FILTERS =
				Map.of("byName", val -> Criteria.where("name").is(val));

		public EvolutionProblemDbHandler(R2dbcEntityTemplate tmpl) {
			super(EvolutionProblem.class, tmpl, ENTITY_FILTERS, _ -> null);
		}
	}
}
