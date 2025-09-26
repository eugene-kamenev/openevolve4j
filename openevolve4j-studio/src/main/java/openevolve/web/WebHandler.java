package openevolve.web;

import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import openevolve.db.DbHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static openevolve.web.WebUtil.all;
import static openevolve.web.WebUtil.ok;
import static openevolve.web.WebUtil.count;
import static openevolve.web.WebUtil.list;

public class WebHandler<E> {

	protected final DbHandler<E> dbHandler;
	protected final ObjectMapper mapper;

	public WebHandler(DbHandler<E> dbHandler, ObjectMapper mapper) {
		this.dbHandler = dbHandler;
		this.mapper = mapper;
	}

	public Mono<ServerResponse> index(ServerRequest request) {
		return list(listResources(request), countResources(request));
	}

	public Mono<ServerResponse> get(ServerRequest request) {
		return ok(dbHandler.findById(request.pathVariable("id")));
	}

	public Mono<ServerResponse> create(ServerRequest request) {
		return ok(request.bodyToMono(dbHandler.getEntityClass()).flatMap(dbHandler::save)
				.onErrorMap(Throwable.class,
						e -> new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage())));
	}

	public Mono<ServerResponse> update(ServerRequest request) {
		String id = request.pathVariable("id");

		return dbHandler.findById(id)
				.switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
				.zipWith(request.bodyToMono(String.class))
				.map(tuple -> updateEntityFromJson(tuple.getT1(), tuple.getT2()))
				.flatMap(dbHandler::update).flatMap(entity -> ok(Mono.just(entity)))
				.onErrorMap(IllegalArgumentException.class,
						e -> new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()));
	}

	public Mono<ServerResponse> delete(ServerRequest request) {
		return dbHandler.deleteById(request.pathVariable("id"))
				.then(ServerResponse.noContent().build()).onErrorResume(
						IllegalArgumentException.class, _ -> ServerResponse.notFound().build());
	}

	protected Flux<E> listResources(ServerRequest request) {
		Map<String, Object> filters = getParsedFilters(request);
		return all(dbHandler, filters, sort(request), offset(request), limit(request));
	}

	protected Mono<Long> countResources(ServerRequest request) {
		Map<String, Object> filters = getParsedFilters(request);
		return count(dbHandler, filters);
	}

	protected Map<String, Object> getParsedFilters(ServerRequest request) {
		try {
			var filters = request.queryParams().getFirst("filters");
			return parseFilters(filters);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
	}

	protected Sort sort(ServerRequest request) {
		return WebUtil.sort(request);
	}

	protected long offset(ServerRequest request) {
		return WebUtil.offset(request);
	}

	protected int limit(ServerRequest request) {
		return WebUtil.limit(request);
	}

	@SuppressWarnings("unchecked")
	private E updateEntityFromJson(E existingEntity, String updatesJson) {
		try {
			// Use Jackson's readerForUpdating to apply partial updates
			var existingNode = mapper.convertValue(existingEntity, ObjectNode.class);
			var updatesNode = (ObjectNode) mapper.readTree(updatesJson);
			existingNode.setAll(updatesNode);
			return (E) mapper.treeToValue(existingNode, existingEntity.getClass());
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to update entity from JSON", e);
		}
	}

	private Map<String, Object> parseFilters(String filtersJson) {
		if (filtersJson != null && !filtersJson.isBlank()) {
			try {
				return mapper.readValue(filtersJson, new TypeReference<Map<String, Object>>() {});
			} catch (Exception e) {
				throw new IllegalArgumentException("Invalid filters", e);
			}
		}
		return Map.of();
	}
}
