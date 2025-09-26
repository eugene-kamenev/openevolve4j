package openevolve.tree;

import java.util.*;
import java.util.function.Function;
import openevolve.domain.FitnessAware;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Flat PUCT The PUCT (Predictor + Upper Confidence bound applied to Trees). We: - Keep solutions in
 * a TreeSet<UUID> ordered by the provided Comparator<S> + UUID tie-break. - Track visits per
 * solution in a Map<UUID,Integer>. - Use Solution.parentId() to walk ancestors for backpropagation.
 *
 * Notes: - Duplicates by score are allowed because tie-break is by UUID. - Subclasses emit new
 * solutions; their parentId() must be set appropriately.
 */
public abstract class ReactivePuctTree<S extends FitnessAware> {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ReactivePuctTree.class);

    protected final Comparator<Map<String, Object>> comparator;
    protected final double cpuct;

    // Order solutions by comparator, tie-breaking by UUID
    protected final Map<UUID, S> byId = new HashMap<>();
    protected final TreeSet<UUID> ordered;

    // Stats
    protected final Map<UUID, Integer> visits = new HashMap<>();
    protected int totalVisits = 0;

    // Parent chosen for the next expansion
    protected volatile UUID currentParentId;

    public ReactivePuctTree(double cpuct, Comparator<Map<String, Object>> fitnessComparator) {
        this.cpuct = cpuct;
        this.comparator = fitnessComparator;
        this.ordered = new TreeSet<>((a, b) -> {
            S sa = byId.get(a);
            S sb = byId.get(b);
            if (sa != null && sb != null) {
                int c = comparator.compare(sa.fitness(), sb.fitness());
                if (c != 0)
                    return c;
            }
            return a.compareTo(b);
        });
    }

    protected abstract List<? extends Function<S, Mono<S>>> workers();

    protected abstract Mono<S> root();

    public Flux<S> run() {
        return Flux.defer(() -> {
            Flux<S> rootFlux = root().subscribeOn(Schedulers.boundedElastic())
                    .flux();

            List<Flux<S>> workerFluxes = workers().stream().map(this::worker)
                    .map(flux -> flux.subscribeOn(Schedulers.boundedElastic()))
                    .toList();

            Flux<S> workersFlux = Flux.merge(workerFluxes);

            return rootFlux.concatWith(workersFlux).doOnNext(this::backpropagate);
        });
    }

    private Flux<S> worker(Function<S, Mono<S>> mapper) {
        return Mono.defer(() -> Mono.just(getCurrentParent())
                .flatMap(parent -> mapper.apply(parent).onErrorResume(e -> {
                    log.error("Error in worker function for solution id: {}", parent.id(), e);
                    return Mono.empty();
                }))).repeat();
    }

    protected void backpropagate(S s) {
        UUID id = s.id();
        synchronized (ordered) {
            // Ignore duplicates by ID (shouldn't normally happen)
            if (byId.containsKey(id)) {
                return;
            }

            // Register solution and initial visit
            byId.put(id, s);
            visits.put(id, 1);
            ordered.add(id);

            // Count new node visit
            totalVisits += 1;

            // Propagate visit up the ancestor chain using parentId()
            UUID parentId = s.parentId();
            while (parentId != null) {
                visits.merge(parentId, 1, Integer::sum);
                totalVisits += 1;

                S parent = byId.get(parentId);
                if (parent == null)
                    break; // parent not seen yet; stop safely
                parentId = parent.parentId();
            }

            nextParent();
        }
    }

    public S getBestByComparator() {
        synchronized (ordered) {
            if (ordered.isEmpty())
                return null;
            return byId.get(ordered.last()); // comparator ascending => last is best
        }
    }

    public S getCurrentParent() {
        if (currentParentId == null) {
            throw new IllegalStateException("No current parent selected");
        }
        return byId.get(currentParentId);
    }

    private void nextParent() {
        int N_total = Math.max(1, totalVisits);

        UUID selected = null;
        double bestPuctScore = Double.NEGATIVE_INFINITY;

        int n = ordered.size();
        if (n == 0) {
            currentParentId = null;
            return;
        }

        double pFlat = 1.0 / n;
        int rank = 0;

        for (UUID id : ordered) {
            rank++;
            int v = visits.getOrDefault(id, 1);

            double rankScore = (n == 1) ? 1.0 : (rank - 1) / (double) (n - 1);
            double U = rankScore + cpuct * pFlat * Math.sqrt(N_total) / (1.0 + v);

            if (U > bestPuctScore) {
                bestPuctScore = U;
                selected = id;
            }
        }

        this.currentParentId = selected;
    }
}
