package openevolve.db;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.validation.Errors;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DbHandler<E> {

	final R2dbcEntityTemplate tmpl;
	final Class<E> entityClass;
	final Map<String, Function<Object, Criteria>> entityFilters;
	final Function<E, Errors> entityValidator;

	public DbHandler(Class<E> entityClass, R2dbcEntityTemplate tmpl,
			Map<String, Function<Object, Criteria>> entityFilters, Function<E, Errors> entityValidator) {
		this.tmpl = tmpl;
		this.entityClass = entityClass;
		this.entityFilters = entityFilters;
		this.entityValidator = entityValidator;
	}

	public DbHandler(Class<E> entityClass, R2dbcEntityTemplate tmpl, ObjectMapper mapper) {
		this(entityClass, tmpl, Map.of(), _ -> null);
	}

	public Flux<E> findAll(Query query) {
		return tmpl.select(query, entityClass);
	}

	public Flux<E> findAllPaginated(Query query, Sort sort, long offset, int limit) {
		return tmpl.select(query.sort(sort).offset(offset).limit(limit), entityClass);
	}

	public Mono<E> findById(Serializable id) {
		var idColumn = tmpl.getConverter().getMappingContext().getPersistentEntity(entityClass).getIdColumn();
		return tmpl.selectOne(Query.query(Criteria.where(idColumn.getReference()).is(id)), entityClass);
	}

	public Mono<E> findOne(Query query) {
		return tmpl.selectOne(query, entityClass);
	}

	public Mono<Long> count(Query query) {
		return tmpl.count(query, entityClass);
	}

	public Mono<E> save(E entity) {
		return validateEntity(entity)
				.then(tmpl.insert(entity));
	}

	public Mono<E> update(E entity) {
		return validateEntity(entity)
				.then(tmpl.update(entity));
	}

	public Mono<Void> delete(E entity) {
		return validateEntity(entity)
				.then(tmpl.delete(entity).then());
	}

	public Mono<Void> deleteById(Serializable id) {
		return findById(id)
				.switchIfEmpty(Mono.error(new IllegalArgumentException("Entity not found")))
				.flatMap(this::delete);
	}

	public Query query(Map<String, Object> filters) {
		if (!filters.isEmpty()) {
			List<Criteria> criterias = new ArrayList<>();
			for (var f : filters.entrySet()) {
				var entityFilter = entityFilters.get(f.getKey());
				if (entityFilter != null) {
					criterias.add(entityFilter.apply(f.getValue()));
				}
			}
			return Query.query(Criteria.from(criterias));
		}
		return Query.empty();
	}

	public Class<E> getEntityClass() {
		return entityClass;
	}

	private Mono<Void> validateEntity(E entity) {
		if (entityValidator != null) {
			Errors errors = entityValidator.apply(entity);
			if (errors != null && errors.hasErrors()) {
				return Mono.error(new ValidationException(errors));
			}
		}
		return Mono.empty();
	}

	public static class ValidationException extends RuntimeException {
		public ValidationException(Errors errors) {
			super("Validation failed: " + errors.getAllErrors().toString());
		}
	}
}
