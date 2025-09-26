package openevolve.web;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import openevolve.db.DbHandler;
import openevolve.db.DbHandlers.EventDbHandler;
import openevolve.service.OpenEvolveService;
import openevolve.studio.domain.LLMModel;
import openevolve.studio.domain.Problem;
import reactor.core.publisher.Mono;

import static openevolve.web.WebUtil.all;
import static openevolve.web.WebUtil.count;
import static openevolve.web.WebUtil.ok;
import static openevolve.web.WebUtil.list;

@Configuration
public class WebHandlers {

    @Bean
    RouterFunction<ServerResponse> routeLLMModel(LLMModelHandler llmModelHandler) {
        return WebUtil.defaultRoutes("/models", llmModelHandler);
    }

    @Bean
    RouterFunction<ServerResponse> routeProblems(ProblemWebHandler handler) {
        HandlerFunction<ServerResponse> events = handler::listEvents;
        return WebUtil.defaultRoutes("/problems", handler, (builder) -> builder
                .GET("/problems/{id}/events", events)
                .GET("/problems/{id}/solutions", events)
                .GET("/problems/{id}/solutions/{solutionId}/ancestors", handler::listAncestors)
                .GET("/problems/{id}/solutions/{solutionId}/children", handler::listChildren)
                .GET("/problems/{id}/runs", events)
                .POST("problems/{id}/start", handler::start)
                .POST("problems/{id}/stop", handler::stop)
                .GET("problems/status", handler::status));
    }

    @Component
    public static class LLMModelHandler extends WebHandler<LLMModel> {
        public LLMModelHandler(DbHandler<LLMModel> dbHandler, ObjectMapper mapper) {
            super(dbHandler, mapper);
        }
    }

    @Component
    public static class ProblemWebHandler extends WebHandler<Problem> {

        private final EventDbHandler eventDbHandler;
        private final OpenEvolveService openEvolveService;
        private static final Mono<Map<String, String>> STARTED =
                Mono.defer(() -> Mono.just(Map.of("status", "started")));
        private static final Mono<Map<String, String>> STOPPED =
                Mono.defer(() -> Mono.just(Map.of("status", "stopped")));

        public ProblemWebHandler(DbHandler<Problem> dbHandler, ObjectMapper mapper,
                OpenEvolveService openEvolveService, EventDbHandler eventDbHandler) {
            super(dbHandler, mapper);
            this.openEvolveService = openEvolveService;
            this.eventDbHandler = eventDbHandler;
        }

        Mono<ServerResponse> start(ServerRequest request) {
            UUID id = UUID.fromString(request.pathVariable("id"));
            return ok(openEvolveService.start(id).then(STARTED));
        }

        Mono<ServerResponse> stop(ServerRequest request) {
            UUID id = UUID.fromString(request.pathVariable("id"));
            return ok(openEvolveService.stop(id).then(STOPPED));
        }

        Mono<ServerResponse> status(ServerRequest request) {
            return ok(Mono.just(openEvolveService.getAllStatuses()));
        }

        Mono<ServerResponse> listEvents(ServerRequest request) {
            String problemId = request.pathVariable("id");
            var filters = new HashMap<>(getParsedFilters(request));
            filters.put("forProblem", problemId);
            if (request.path().endsWith("solutions")) {
                filters.put("forType", "SOLUTION");
            } else if (request.path().endsWith("runs")) {
                filters.put("forType", "EVOLUTION_RUN");
            }
            var sort = sort(request);
            var offset = offset(request);
            var limit = limit(request);
            Object sortObj = filters.remove("sort");
            if (sortObj instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Boolean> filterSort = (Map<String, Boolean>) sortObj;
                sort = EventDbHandler.fitnessSort(filterSort);
            }
            return list(all(eventDbHandler, filters, sort, offset, limit),
                    count(eventDbHandler, filters));
        }

        Mono<ServerResponse> listChildren(ServerRequest request) {
            UUID id = UUID.fromString(request.pathVariable("solutionId"));
            var filters = Map.<String, Object>of("forType", "SOLUTION", "forParent", id);
            return ok(eventDbHandler.findAll(eventDbHandler.query(filters)).collectList());
        }

        Mono<ServerResponse> listAncestors(ServerRequest request) {
            UUID problemId = UUID.fromString(request.pathVariable("id"));
            UUID solutionId = UUID.fromString(request.pathVariable("solutionId"));
            return ok(eventDbHandler.ancestors(problemId, solutionId).collectList());
        }
    }
}
