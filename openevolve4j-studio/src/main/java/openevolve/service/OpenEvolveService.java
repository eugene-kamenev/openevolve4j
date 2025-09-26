package openevolve.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import openevolve.Constants;
import openevolve.OpenEvolve;
import openevolve.db.DbHandler;
import openevolve.db.DbHandlers.EventDbHandler;
import openevolve.domain.Solution;
import openevolve.domain.SourceTree;
import openevolve.studio.domain.Event;
import openevolve.studio.domain.Problem;
import openevolve.studio.domain.Event.Run;
import openevolve.tree.PuctTree.Repository;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class OpenEvolveService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(OpenEvolveService.class);

    private final Map<UUID, Disposable> runningTasks = new ConcurrentHashMap<>();

    private final EventBus eventService;
    private final OpenAiApi openAiApi;
    private final DbHandler<Problem> problems;
    private final EventDbHandler events;

    public OpenEvolveService(EventBus eventService, OpenAiApi openAiApi,
            DbHandler<Problem> problemHandler, EventDbHandler eventHandler) {
        this.eventService = eventService;
        this.openAiApi = openAiApi;
        this.problems = problemHandler;
        this.events = eventHandler;
    }

    public Mono<Void> start(UUID problemId) {
        return problems.findById(problemId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "No evolution problem found for id " + problemId)))
                .flatMap(problem -> startEvolution(problem));
    }

    private Mono<Void> startEvolution(Problem problem) {
        return saveEvolutionRun(problem).flatMap(run -> {
            var algo = OpenEvolve.create(problem.config(), openAiApi,
                    new SolutionRepository(problem.id(), run.id(), events),
                    Constants.OBJECT_MAPPER);
            return startProcess(problem.id(), algo.run().index()
                    .takeUntil(t -> t.getT1() > problem.config().iterations()).then());
        });
    }

    private Mono<Run> saveEvolutionRun(Problem problem) {
        var run = new Run(UUID.randomUUID());
        var event = new Event<>(UUID.randomUUID(), problem.id(), Instant.now(), run);
        return events.save(event).thenReturn(run);
    }

    private Mono<Void> startProcess(UUID taskId, Mono<?> task) {
        return stopIfRunning(taskId).then(Mono.defer(() -> {
            var disposable =
                    task.doOnSubscribe(_ -> log.info("Task {} started", taskId)).doFinally(s -> {
                        log.info("Task {} finished with status {}", taskId, s);
                        runningTasks.remove(taskId);
                    }).subscribe();
            runningTasks.put(taskId, disposable);
            return Mono.empty();
        }));
    }

    private Mono<Void> stopIfRunning(UUID taskId) {
        return runningTasks.containsKey(taskId) ? stop(taskId).then() : Mono.empty();
    }

    public Mono<Void> stop(UUID taskId) {
        var disposable = runningTasks.remove(taskId);
        if (disposable != null && !disposable.isDisposed()) {
            log.info("Stopping task {}", taskId);
            disposable.dispose();
        }
        return Mono.empty();
    }

    public String getStatus(UUID taskId) {
        var disposable = runningTasks.get(taskId);
        return (disposable != null && !disposable.isDisposed()) ? "RUNNING" : "NOT_RUNNING";
    }

    public Map<UUID, String> getAllStatuses() {
        return runningTasks.keySet().stream()
                .collect(Collectors.toMap(taskId -> taskId, this::getStatus));
    }

    @Override
    public void destroy() throws Exception {
        runningTasks.values().forEach(Disposable::dispose);
    }

    private static final class SolutionRepository implements Repository<SourceTree> {

        private final UUID problemId;
        private final EventDbHandler events;
        private final UUID runId;

        public SolutionRepository(UUID problemId, UUID runId, EventDbHandler events) {
            this.problemId = problemId;
            this.runId = runId;
            this.events = events;
        }

        @Override
        public Flux<Solution<SourceTree>> ancestors(UUID childId) {
            // temporary stub
            return Flux.empty();
        }

        @Override
        public Flux<Solution<SourceTree>> children(UUID parentId) {
            var filters = Map.<String, Object>of("forProblem", problemId, "forParent", parentId,
                    "forType", "SOLUTION");
            Flux<Event.Solution<SourceTree>> eventFlux =
                    events.cast(events.findAll(events.query(filters)));
            return eventFlux.map(Event.Solution::toCoreSolution);
        }

        @Override
        public Mono<Solution<SourceTree>> get(UUID id) {
            var filters = Map.<String, Object>of("forId", id, "forProblem", problemId, "forType",
                    "SOLUTION");
            Mono<Event.Solution<SourceTree>> event =
                    events.cast(events.findOne(events.query(filters)));
            return event.map(Event.Solution::toCoreSolution);
        }

        @Override
        public Mono<Solution<SourceTree>> save(Solution<SourceTree> solution) {
            return events.save(new Event<>(UUID.randomUUID(), problemId, Instant.now(),
                    Event.Solution.fromCoreSolution(solution, runId))).thenReturn(solution);
        }
    }
}
