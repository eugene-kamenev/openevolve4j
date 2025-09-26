package openevolve.web;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.NullHandling;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.domain.SqlSort.SqlOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import openevolve.db.DbHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static reactor.function.TupleUtils.function;

public final class WebUtil {

	private WebUtil() {}

	public static final Mono<Object> NOT_FOUND =
			Mono.defer(() -> Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)));

	record ListResponse<T>(List<T> list, long count) {
	}

	public static <S> Flux<S> all(DbHandler<S> handler, Map<String, Object> filters,
			Sort sort, long offset, int limit) {
		Query filteredQuery = handler.query(filters);
		return handler.findAllPaginated(filteredQuery, sort, offset, limit);
	}

	public static <S> Mono<Long> count(DbHandler<S> handler, Map<String, Object> filters) {
		Query filteredQuery = handler.query(filters);
		return handler.count(filteredQuery);
	}

	public static Sort sort(ServerRequest request) {
		if (request == null) {
			return Sort.unsorted();
		}
		var sortParam = request.queryParams().getFirst("sort");
		var orderParam = request.queryParams().getFirst("order");
		if (sortParam != null && !sortParam.isBlank() && orderParam != null
				&& !orderParam.isBlank()) {
			var direction = Sort.Direction.ASC;
			if (orderParam.equalsIgnoreCase("desc")) {
				direction = Sort.Direction.DESC;
			}
			return Sort.by(SqlOrder.by(sortParam).with(direction).with(NullHandling.NULLS_LAST)
					.withUnsafe());
		}
		return Sort.unsorted();
	}

	public static long offset(ServerRequest request) {
		if (request == null) {
			return 0;
		}
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

	public static int limit(ServerRequest request) {
		int limit = 10;
		if (request == null) {
			return limit;
		}
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

	public static <P> Mono<ServerResponse> ok(Mono<P> body) {
		return body.flatMap(
				b -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(b))
				.switchIfEmpty(ServerResponse.notFound().build());
	}

	public static RouterFunction<ServerResponse> defaultRoutes(String basePath,
			WebHandler<?> handler, Consumer<RouterFunctions.Builder> custom) {
		var builder = RouterFunctions.route();
		custom.accept(builder);
		builder.GET(basePath, handler::index).GET(basePath + "/{id}", handler::get)
				.POST(basePath, handler::create).PUT(basePath + "/{id}", handler::update)
				.DELETE(basePath + "/{id}", handler::delete);
		return builder.build();
	}

	public static RouterFunction<ServerResponse> defaultRoutes(String basePath,
			WebHandler<?> handler) {
		return defaultRoutes(basePath, handler, _ -> {
		});
	}

	public static <S> Mono<ServerResponse> list(Flux<S> resources, Mono<Long> count) {
		return ok(
				Mono.zip(resources.collectList(), count).map(function(ListResponse::new)));
	}
}
