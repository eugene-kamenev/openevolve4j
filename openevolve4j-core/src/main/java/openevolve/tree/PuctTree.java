package openevolve.tree;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import openevolve.domain.EvolutionStep;
import openevolve.domain.FitnessAware;
import openevolve.domain.Solution;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class PuctTree<T extends FitnessAware.Data> extends ReactivePuctTree<Solution<T>> {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PuctTree.class);

    private final List<? extends Function<EvolutionStep<Solution<T>>, T>> agents;
    private final Function<T, Map<String, Object>> evaluator;
    private final Supplier<T> rootSupplier;
    private final Repository<T> repository;

    public PuctTree(double cpuct, Comparator<Map<String, Object>> comparator, Repository<T> manager,
            Supplier<T> root, List<? extends Function<EvolutionStep<Solution<T>>, T>> agents,
            Function<T, Map<String, Object>> evaluator) {
        super(cpuct, comparator);
        if (agents == null || agents.isEmpty() || evaluator == null) {
            throw new IllegalArgumentException("agents and evaluators must not be null or empty");
        }
        this.repository = manager;
        this.rootSupplier = root;
        this.agents = agents;
        this.evaluator = evaluator;
    }

    @Override
    protected Mono<Solution<T>> root() {
        return evaluateAndSave(null, rootSupplier.get());
    }

    @Override
    protected List<? extends Function<Solution<T>, Mono<Solution<T>>>> workers() {
        return agents.stream().map(this::worker).toList();
    }

    private Function<Solution<T>, Mono<Solution<T>>> worker(
            Function<EvolutionStep<Solution<T>>, T> agentFunc) {
        return (solution) -> {
            return Mono
                    .zip(repository.get(solution.id()),
                            repository.children(solution.id()).collectList(),
                            repository.ancestors(solution.id()).collectList())
                    .map(tuple -> new EvolutionStep<>(tuple.getT1(), tuple.getT2(), tuple.getT3()))
                    .flatMap(step -> Mono.fromCallable(() -> agentFunc.apply(step))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(fileData -> evaluateAndSave(solution, fileData)));
        };
    }

    private Mono<Solution<T>> evaluateAndSave(Solution<T> parent, T data) {
        var parentId = parent != null ? parent.id() : null;
        if (data == null) {
            log.error("Returned null data for parentId: {}", parentId);
            return Mono.empty();
        }
        return Mono.fromCallable(() -> evaluator.apply(data))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(fitness -> repository
                        .save(new Solution<>(UUID.randomUUID(), parentId, fitness, data))
                        .map(this::withOutData));
    }

    // just to avoid storing large data in memory
    private Solution<T> withOutData(Solution<T> solution) {
        return new Solution<>(solution.id(), solution.parentId(), solution.fitness(), null);
    }

    public static interface Repository<T extends FitnessAware.Data> {
        Mono<Solution<T>> get(UUID id);

        Flux<Solution<T>> children(UUID parentId);

        Flux<Solution<T>> ancestors(UUID childId);

        Mono<Solution<T>> save(Solution<T> solution);
    }
}
