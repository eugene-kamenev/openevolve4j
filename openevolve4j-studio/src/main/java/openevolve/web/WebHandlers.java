package openevolve.web;

import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import openevolve.db.DbHandler;
import openevolve.db.EvolutionProblem;
import openevolve.db.EvolutionRun;
import openevolve.db.EvolutionSolution;
import openevolve.db.EvolutionState;
import openevolve.db.LLMModel;
import openevolve.db.DbHandlers.EvolutionSolutionDbHandler;
import openevolve.service.OpenEvolveService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Configuration
public class WebHandlers {

    @Bean
    RouterFunction<ServerResponse> routeLLMModel(LLMModelHandler llmModelHandler) {
        return WebHandler.defaultRoutes("/models", llmModelHandler);
    }

    @Bean
    RouterFunction<ServerResponse> routeEvolutionRun(EvolutionRunHandler evolutionRunHandler) {
        return WebHandler.defaultRoutes("/run", evolutionRunHandler, builder -> builder
                .POST("/run/start", evolutionRunHandler::start)
                .POST("/run/{id}/stop", evolutionRunHandler::stop)
                .GET("/run/status", evolutionRunHandler::status));
    }

    @Bean
    RouterFunction<ServerResponse> routeEvolutionState(
            EvolutionStateHandler evolutionStateHandler) {
        return WebHandler.defaultRoutes("/state", evolutionStateHandler);
    }

    @Bean
    RouterFunction<ServerResponse> routeEvolutionSolution(
            EvolutionSolutionHandler evolutionSolutionHandler) {
        return WebHandler.defaultRoutes("/solution", evolutionSolutionHandler, builder -> builder
                .GET("/solution/{id}/parents", evolutionSolutionHandler::getAllParents));
    }

    @Bean
    RouterFunction<ServerResponse> routeEvolution(EvolutionHandler evolutionHandler) {
        return WebHandler.defaultRoutes("/evolution", evolutionHandler);
    }

    public record StartCommand(UUID problemId, UUID runId, Set<UUID> solutionIds) {
    }

    @Component
    public static class LLMModelHandler extends WebHandler<LLMModel> {
        public LLMModelHandler(DbHandler<LLMModel> dbHandler, ObjectMapper mapper) {
            super(dbHandler, mapper);
        }
    }

    @Component
    public static class EvolutionHandler extends WebHandler<EvolutionProblem> {
        public EvolutionHandler(DbHandler<EvolutionProblem> dbHandler, ObjectMapper mapper) {
            super(dbHandler, mapper);
        }
    }

    @Component
    public static class EvolutionSolutionHandler extends WebHandler<EvolutionSolution> {
        public EvolutionSolutionHandler(DbHandler<EvolutionSolution> dbHandler,
                ObjectMapper mapper) {
            super(dbHandler, mapper);
        }

        public Mono<ServerResponse> getAllParents(ServerRequest request) {
            var casted = (EvolutionSolutionDbHandler) dbHandler;
            return okResponse(
                    Flux.from(casted.findAllParents(UUID.fromString(request.pathVariable("id"))))
                            .collectList());
        }
    }

    @Component
    public static class EvolutionStateHandler extends WebHandler<EvolutionState> {
        public EvolutionStateHandler(DbHandler<EvolutionState> dbHandler, ObjectMapper mapper) {
            super(dbHandler, mapper);
        }
    }

    @Component
    public static class EvolutionRunHandler extends WebHandler<EvolutionRun> {
        private final OpenEvolveService service;

        public EvolutionRunHandler(DbHandler<EvolutionRun> dbHandler, OpenEvolveService service,
                ObjectMapper mapper) {
            super(dbHandler, mapper);
            this.service = service;
        }

        public Mono<ServerResponse> start(ServerRequest request) {
            return okResponse(request.bodyToMono(StartCommand.class).flatMap(service::start));
        }

        public Mono<ServerResponse> stop(ServerRequest request) {
            var id = UUID.fromString(request.pathVariable("id"));
            return okResponse(service.stopProcess(id));
        }

        public Mono<ServerResponse> status(ServerRequest request) {
            return okResponse(Mono.just(service.getAllStatuses()));

        }
    }
}
