package openevolve.puct;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import openevolve.EvolveSolution;
import openevolve.EvolveStep;
import openevolve.OpenEvolveAgent;
import openevolve.OpenEvolveEvaluator;
import openevolve.api.Algorithm;
import openevolve.mapelites.Population.Solution;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class LLMPuctTree extends PuctTreeSearch<Solution<EvolveSolution>> implements Algorithm {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(LLMPuctTree.class);

    private final List<OpenEvolveAgent> agents;
    private final OpenEvolveEvaluator evaluator;
    private final Supplier<EvolveSolution> rootSupplier;
    private final SolutionManager manager;
    private final UUID runId;

    public LLMPuctTree(UUID runId, SolutionManager manager, Supplier<EvolveSolution> root,
            double cpuct, Comparator<Solution<EvolveSolution>> comparator,
            List<OpenEvolveAgent> agents, OpenEvolveEvaluator evaluator) {
        super(cpuct, comparator);
        if (agents == null || agents.isEmpty() || evaluator == null) {
            throw new IllegalArgumentException("agents and evaluators must not be null or empty");
        }
        this.runId = runId;
        this.manager = manager;
        this.rootSupplier = root;
        this.agents = agents;
        this.evaluator = evaluator;
    }

    @Override
    protected Mono<Solution<EvolveSolution>> initRoot() {
        return Mono.just(rootSupplier.get()).flatMap(s -> evaluateAndSave(null, s));
    }

    @Override
    protected Flux<Node<Solution<EvolveSolution>>> evolve() {
        // One worker per agent; each worker repeatedly performs one selection+expansion+evaluation step.
        return Flux.range(0, agents.size())
                .flatMap(i -> worker(agents.get(i)), agents.size());
    }

    private Flux<Node<Solution<EvolveSolution>>> worker(OpenEvolveAgent agent) {
        return Mono.defer(() -> {
                    Node<Solution<EvolveSolution>> parent = nextParent();
                    if (parent == null) return Mono.empty();

                    Mono<Solution<EvolveSolution>> parentSolutionMono =
                            getParent(parent.solution.id()).switchIfEmpty(Mono.just(parent.solution));

                    return parentSolutionMono
                            .flatMap(sol -> evolveAgent(agent, new EvolveStep(sol, List.of(), List.of(), List.of())))
                            .flatMap(child -> evaluateAndSave(parent, child))
                            .map(saved -> new Node<>(saved, parent));
                })
                .repeat(); // repeat indefinitely; upstream 'take(iterations)' will cancel
    }

    private Mono<Solution<EvolveSolution>> evaluateAndSave(Node<Solution<EvolveSolution>> parent,
            EvolveSolution solution) {
        return Mono.fromCallable(() -> createSolution(solution, evaluator.apply(solution), parent))
                .flatMap(this::save)
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> {
                    log.error("Evaluation failed", ex);
                    return Mono.empty();
                });
    }

    private Mono<EvolveSolution> evolveAgent(OpenEvolveAgent agent, EvolveStep step) {
        return Mono.fromCallable(() -> agent.apply(step))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> {
                    log.error("Agent failed", ex);
                    return Mono.empty();
                });
    }

    private Mono<Solution<EvolveSolution>> getParent(UUID parentId) {
        if (parentId == null) {
            return Mono.empty();
        }
        return manager.get(parentId);
    }

    private Mono<Solution<EvolveSolution>> save(Solution<EvolveSolution> solution) {
        return manager.save(solution);
    }

    private Solution<EvolveSolution> createSolution(EvolveSolution evolveSolution,
            Map<String, Object> fitness, Node<Solution<EvolveSolution>> parent) {
        if (parent == null) {
            // root node
            return new Solution<>(UUID.randomUUID(), null, runId, Instant.now(), evolveSolution,
                    fitness, null);
        }
        return new Solution<>(UUID.randomUUID(), parent.solution.id(), parent.solution.runId(),
                Instant.now(), evolveSolution, fitness, null);
    }

    public static interface SolutionManager {
        Mono<Solution<EvolveSolution>> get(UUID id);

        Mono<Solution<EvolveSolution>> save(Solution<EvolveSolution> solution);
    }
}
