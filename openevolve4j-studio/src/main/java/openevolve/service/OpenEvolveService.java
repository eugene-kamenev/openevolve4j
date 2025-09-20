package openevolve.service;

import java.time.Instant;
import java.util.List;
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
import openevolve.EvolveSolution;
import openevolve.OpenEvolve;
import openevolve.OpenEvolveConfig;
import openevolve.db.DbHandler;
import openevolve.db.EvolutionProblem;
import openevolve.db.EvolutionRun;
import openevolve.db.EvolutionSolution;
import openevolve.db.EvolutionState;
import openevolve.events.EventListener;
import openevolve.events.Event.Started;
import openevolve.events.Event.Stopped;
import openevolve.mapelites.MAPElites;
import openevolve.mapelites.Population.Solution;
import openevolve.mapelites.listener.MAPElitesLoggingListener;
import openevolve.puct.LLMPuctTreeConfig;
import openevolve.puct.LLMPuctTree.SolutionManager;
import openevolve.web.WebHandlers.StartCommand;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class OpenEvolveService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(OpenEvolveService.class);

    private final Map<UUID, Disposable> runningTasks = new ConcurrentHashMap<>();

    private final EventBus eventService;
    private final OpenAiApi openAiApi;
    private final DbHandler<EvolutionSolution> solutionHandler;
    private final DbHandler<EvolutionState> stateHandler;
    private final DbHandler<EvolutionRun<?>> runHandler;
    private final DbHandler<EvolutionProblem<?>> problemHandler;

    public OpenEvolveService(EventBus eventService, OpenAiApi openAiApi,
            DbHandler<EvolutionSolution> solutionHandler, DbHandler<EvolutionState> stateHandler,
            DbHandler<EvolutionRun<?>> runHandler, DbHandler<EvolutionProblem<?>> problemHandler) {
        this.eventService = eventService;
        this.openAiApi = openAiApi;
        this.solutionHandler = solutionHandler;
        this.stateHandler = stateHandler;
        this.runHandler = runHandler;
        this.problemHandler = problemHandler;
    }

    public Mono<EvolutionRun<?>> start(StartCommand startCommand) {
        return problemHandler.findById(startCommand.problemId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "No evolution problem found for id " + startCommand.problemId())))
                .flatMap(problem -> startEvolution(startCommand, problem));
    }

    private Mono<EvolutionRun<?>> startEvolution(StartCommand startCommand,
            EvolutionProblem<?> problem) {
        if (problem.config() instanceof LLMPuctTreeConfig config) {
            return saveEvolutionRun(startCommand, problem).flatMap(run -> {
                var algo = OpenEvolve.create(run.id(), config,
                        openAiApi, new SolutionManager() {
                            @Override
                            public Mono<Solution<EvolveSolution>> get(UUID id) {
                                return solutionHandler.findById(id)
                                        .map(EvolutionSolution::toPopulationSolution);
                            };

                            @Override
                            public Mono<Solution<EvolveSolution>> save(
                                    Solution<EvolveSolution> solution) {
                                return solutionHandler
                                        .save(EvolutionSolution.fromPopulationSolution(solution,
                                                startCommand.problemId()))
                                        .map(EvolutionSolution::toPopulationSolution)
                                        .map(sol -> new Solution<>(sol.id(), sol.parentId(),
                                                sol.runId(), sol.dateCreated(), null, sol.fitness(),
                                                null));
                            };
                        }, Constants.OBJECT_MAPPER);
                var runner = algo.run(100);
                return startProcess(run.id(), runner).thenReturn(run);
            });
        }
        var listener = new EventListener(eventService, startCommand.problemId());
        return createMAPElites(startCommand, problem, listener).flatMap(mapElites -> {
            setupListeners(mapElites, listener);
            return saveEvolutionRun(startCommand, problem)
                    .flatMap(run -> startEvolutionProcess(startCommand.problemId(), mapElites,
                            problem, run));
        });
    }

    private Mono<MAPElites<EvolveSolution>> createMAPElites(StartCommand startCommand,
            EvolutionProblem<?> problem, EventListener listener) {

        if (startCommand.runId() != null) {
            return resumeFromRun(startCommand, listener);
        } else if (startCommand.solutionIds() != null && !startCommand.solutionIds().isEmpty()) {
            return startFromSolutions(startCommand, problem, listener);
        } else {
            return Mono.just(OpenEvolve.create((OpenEvolveConfig) problem.config(),
                    Constants.OBJECT_MAPPER, List.of(), openAiApi, List.of(listener)));
        }
    }

    private Mono<MAPElites<EvolveSolution>> resumeFromRun(StartCommand startCommand,
            EventListener listener) {
        var runMono = runHandler.findById(startCommand.runId());
        var stateMono =
                stateHandler.findOne(stateHandler.query(Map.of("forRun", startCommand.runId())));
        var solutionsMono = solutionHandler
                .findAll(solutionHandler.query(Map.of("forRun", startCommand.runId())))
                .map(EvolutionSolution::toPopulationSolution).collectList();

        return Mono.zip(runMono, stateMono, solutionsMono).map(tuple -> {
            var run = tuple.getT1();
            var state = tuple.getT2();
            var solutions = tuple.getT3();
            return OpenEvolve.create((OpenEvolveConfig) run.config(), Constants.OBJECT_MAPPER, List.of(), openAiApi,
                    List.of(listener)).reset(state.state(), run.id(), solutions);
        });
    }

    private Mono<MAPElites<EvolveSolution>> startFromSolutions(StartCommand startCommand,
            EvolutionProblem<?> problem, EventListener listener) {

        return solutionHandler
                .findAll(solutionHandler.query(Map.of("forIds", startCommand.solutionIds())))
                .map(EvolutionSolution::toPopulationSolution).map(Solution::solution).collectList()
                .flatMap(solutions -> {
                    if (solutions.isEmpty()) {
                        return Mono.error(new IllegalArgumentException(
                                "No solutions found for provided IDs"));
                    }
                    return Mono.just(OpenEvolve.create((OpenEvolveConfig) problem.config(),
                            Constants.OBJECT_MAPPER, solutions, openAiApi, List.of(listener)));
                });
    }

    private void setupListeners(MAPElites<EvolveSolution> mapElites, EventListener listener) {
        mapElites.addListener(listener);
        mapElites.addListener(new MAPElitesLoggingListener<>());
    }

    private Mono<EvolutionRun<?>> saveEvolutionRun(StartCommand startCommand,
            EvolutionProblem<?> problem) {
        var run = new EvolutionRun<>(UUID.randomUUID(), startCommand.problemId(), Instant.now(),
                problem.config());
        return startCommand.runId() != null
                ? runHandler.findById(startCommand.runId())
                        .flatMap(r -> runHandler.update(new EvolutionRun<>(r.id(), r.problemId(),
                                r.dateCreated(), problem.config())))
                : runHandler.save(run);
    }

    private Mono<EvolutionRun<?>> startEvolutionProcess(UUID problemId,
            MAPElites<EvolveSolution> mapElites, EvolutionProblem<?> problem, EvolutionRun<?> run) {

        var evolutionTask = Mono
                .fromRunnable(() -> mapElites
                        .run(((OpenEvolveConfig) problem.config()).mapelites().numIterations()))
                .subscribeOn(Schedulers.boundedElastic());
        mapElites.setRunId(run.id());
        return startProcess(run.id(), evolutionTask).thenReturn(run);
    }

    public Mono<Started> startProcess(UUID taskId, Mono<?> task) {
        return stopIfRunning(taskId).then(Mono.defer(() -> {
            var disposable =
                    task.doOnSubscribe(_ -> log.info("Task {} started", taskId)).doFinally(s -> {
                        log.info("Task {} finished with status {}", taskId, s);
                        runningTasks.remove(taskId);
                    }).subscribe();

            runningTasks.put(taskId, disposable);
            return Mono.just(new Started(taskId.toString()));
        }));
    }

    private Mono<Void> stopIfRunning(UUID taskId) {
        return runningTasks.containsKey(taskId) ? stopProcess(taskId).then() : Mono.empty();
    }

    public Mono<Stopped> stopProcess(UUID taskId) {
        log.info("Stopping task {}", taskId);

        var disposable = runningTasks.remove(taskId);

        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
            return Mono.just(new Stopped(taskId.toString()));
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
}
