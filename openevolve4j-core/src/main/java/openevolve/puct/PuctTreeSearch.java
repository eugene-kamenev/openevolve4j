package openevolve.puct;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Generic, abstract PUCT (Predictor + Upper Confidence bound applied to Trees) tree search.
 *
 * - S: solution type (user-defined) - The user provides a Comparator<S> that orders solutions
 * ascending (smallest -> largest). Rank_T(u) = 1 for the smallest element returned by that
 * comparator.
 *
 * Notes: - Score values themselves are NOT used in the PUCT formula; ranking comes from the
 * comparator. - TreeSet is used to keep nodes sorted for rank computation; tie-break is by unique
 * id so duplicates are allowed.
 */
public abstract class PuctTreeSearch<S> {

    // Node type is static so can be constructed before the outer instance
    public static class Node<S> {
        private static final AtomicInteger ID_GEN = new AtomicInteger(0);

        public final int id;
        public final S solution;
        public final Node<S> parent;
        public int visits;
        public final List<Node<S>> children = new ArrayList<>();

        public Node(S solution, Node<S> parent) {
            this.id = ID_GEN.incrementAndGet();
            this.solution = solution;
            this.parent = parent;
            this.visits = 1;
        }

        public Node(S solution) {
            this(solution, null);
        }

        @Override
        public String toString() {
            return String.format("Node{id=%d, visits=%d, solution=%s}", id, visits, solution);
        }
    }

    // Sorted set of nodes ordered by user comparator (ascending). Tie-break by node id to allow
    // duplicates.
    protected final TreeSet<Node<S>> sortedNodes;
    protected final double cpuct;

    public PuctTreeSearch(double cpuct, Comparator<S> solutionComparator) {
        this.cpuct = cpuct;
        Comparator<Node<S>> nodeComparator = (a, b) -> {
            int c = solutionComparator.compare(a.solution, b.solution);
            if (c != 0)
                return c;
            return Integer.compare(a.id, b.id);
        };
        this.sortedNodes = new TreeSet<>(nodeComparator);
    }

    /**
     * Run the search for a fixed number of iterations.
     */
    public Mono<Void> run(int iterations) {
        Mono<Void> ensureRoot = Mono.defer(() -> {
            synchronized (sortedNodes) {
                if (!sortedNodes.isEmpty()) {
                    return Mono.empty();
                }
            }
            return initRoot()
                .map(Node::new)
                .doOnNext(n -> {
                    synchronized (sortedNodes) {
                        sortedNodes.add(n);
                    }
                })
                .then();
        });

        // Evolve with workers; downstream 'take(iterations)' controls termination.
        Flux<Node<S>> search = Flux.defer(this::evolve);

        return ensureRoot
            .thenMany(search.doOnNext(this::backpropagate).take(iterations))
            .then();
    }

    protected Node<S> nextParent() {
        synchronized (sortedNodes) {
            if (sortedNodes.isEmpty()) return null;

            int N_total = sortedNodes.stream().mapToInt(n -> n.visits).sum();
            if (N_total < 1) N_total = 1;

            Node<S> selected = null;
            double bestVal = Double.NEGATIVE_INFINITY;
            double pFlat = 1.0 / sortedNodes.size(); // flat prior
            int n = sortedNodes.size();
            int idx = 0;

            for (Node<S> u : sortedNodes) {
                idx++;
                double rankScore;
                if (n == 1) {
                    rankScore = 1.0;
                } else {
                    double rank = idx; // 1..n ascending
                    rankScore = (rank - 1) / (double) (n - 1);
                }

                double U = rankScore + cpuct * pFlat * Math.sqrt(N_total) / (1.0 + u.visits);
                if (U > bestVal) {
                    bestVal = U;
                    selected = u;
                }
            }
            return selected;
        }
    }

    protected void backpropagate(Node<S> node) {
        synchronized (sortedNodes) {
            sortedNodes.add(node);
            Node<S> anc = node.parent;
            if (anc != null) {
                anc.children.add(node);
            }
            while (anc != null) {
                anc.visits += 1;
                anc = anc.parent;
            }
        }
    }

    // ...existing code...
    protected abstract Flux<Node<S>> evolve();

    protected abstract Mono<S> initRoot();

    /** Return the node considered best by the user comparator (largest element). */
    public Node<S> getBestByComparator() {
        synchronized (sortedNodes) {
            if (sortedNodes.isEmpty()) return null;
            return sortedNodes.last(); // since comparator is ascending, last is largest/best
        }
    }
}
