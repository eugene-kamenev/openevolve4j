package openevolve.web;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import openevolve.db.DbHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static reactor.function.TupleUtils.function;

public class WebHandler<E> {

	public static final Mono<Object> NOT_FOUND =
			Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND));

	final DbHandler<E> dbHandler;
	final ObjectMapper mapper;

	public WebHandler(DbHandler<E> dbHandler, ObjectMapper mapper) {
		this.dbHandler = dbHandler;
		this.mapper = mapper;
	}

	public Mono<ServerResponse> index(ServerRequest request) {
		return okResponse(Mono.zip(listResources(request).collectList(), countResources(request))
				.map(function(IndexResponse::new)));
	}

	public Mono<ServerResponse> get(ServerRequest request) {
		return okResponse(dbHandler.findById(request.pathVariable("id")));
	}

	public Mono<ServerResponse> create(ServerRequest request) {
		return okResponse(request.bodyToMono(dbHandler.getEntityClass()).flatMap(dbHandler::save)
				.onErrorMap(Throwable.class,
						e -> new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage())));
	}

	public Mono<ServerResponse> update(ServerRequest request) {
    String id = request.pathVariable("id");
    
    return dbHandler.findById(id)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
            .zipWith(request.bodyToMono(String.class))
            .map(tuple -> updateEntityFromJson(tuple.getT1(), tuple.getT2()))
            .flatMap(dbHandler::update)
            .flatMap(entity -> okResponse(Mono.just(entity)))
            .onErrorMap(IllegalArgumentException.class,
                    e -> new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()));
}

	public Mono<ServerResponse> delete(ServerRequest request) {
		return dbHandler.deleteById(request.pathVariable("id"))
				.then(ServerResponse.noContent().build()).onErrorResume(
						IllegalArgumentException.class, _ -> ServerResponse.notFound().build());
	}

	Flux<E> listResources(ServerRequest request) {
		Query filteredQuery = dbHandler.query(getParsedFilters(request));
		return dbHandler.findAllPaginated(filteredQuery, getSort(request), getOffset(request),
				getLimit(request));
	}

	Mono<Long> countResources(ServerRequest request) {
		Query filteredQuery = dbHandler.query(getParsedFilters(request));
		return dbHandler.count(filteredQuery);
	}

	Map<String, Object> getParsedFilters(ServerRequest request) {
		var filters = request.queryParams().getFirst("filters");
		try {
			return parseFilters(filters);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
		}
	}

	Sort getSort(ServerRequest request) {
		var sortParam = request.queryParams().getFirst("sort");
		var orderParam = request.queryParams().getFirst("order");
		if (sortParam != null && !sortParam.isBlank() && orderParam != null
				&& !orderParam.isBlank()) {
			var direction = Sort.Direction.ASC;
			if (orderParam.equalsIgnoreCase("desc")) {
				direction = Sort.Direction.DESC;
			}
			return Sort.by(direction, sortParam);
		}
		return Sort.unsorted();
	}

	long getOffset(ServerRequest request) {
		var offsetParam = request.queryParams().getFirst("offset");
		if (offsetParam != null && !offsetParam.isBlank()) {
			try {
				return Long.parseLong(offsetParam);
			} catch (NumberFormatException e) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid offset", e);
			}
		}
		return 0;
	}

	int getLimit(ServerRequest request) {
		int limit = 10;
		var limitParam = request.queryParams().getFirst("limit");
		if (limitParam != null && !limitParam.isBlank()) {
			try {
				limit = Integer.parseInt(limitParam);
			} catch (NumberFormatException e) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid limit", e);
			}
		}
		return Math.min(limit, 100);
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

	protected static <P> Mono<ServerResponse> okResponse(Mono<P> body) {
		return body.flatMap(
				b -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(b))
				.switchIfEmpty(ServerResponse.notFound().build());
	}

	record IndexResponse<T>(List<T> list, long count) {
	}

	public static RouterFunction<ServerResponse> defaultRoutes(String basePath,
			WebHandler<?> handler, Consumer<RouterFunctions.Builder> custom) {
		var builder = RouterFunctions.route();
		custom.accept(builder);
		builder.GET(basePath, handler::index)
				.GET(basePath + "/{id}", handler::get).POST(basePath, handler::create)
				.PUT(basePath + "/{id}", handler::update)
				.DELETE(basePath + "/{id}", handler::delete);
		return builder.build();
	}

	public static RouterFunction<ServerResponse> defaultRoutes(String basePath,
			WebHandler<?> handler) {
		return defaultRoutes(basePath, handler, _ -> {});
	}
}
