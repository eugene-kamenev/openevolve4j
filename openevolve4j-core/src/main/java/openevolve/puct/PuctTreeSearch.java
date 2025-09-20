// Save as PuctTreeSearch.java
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

    /**
     * @param rootNode initial root node (its visits will be initialized to 1)
     * @param cpuct exploration constant
     * @param solutionComparator comparator ordering solutions ascending (smallest -> largest). If
     *        comparator says two solutions equal, tie-break will use node id.
     */
    public PuctTreeSearch(double cpuct, Comparator<S> solutionComparator) {
        this.cpuct = cpuct;
        // comparator for nodes: first compare by user comparator, then by id to avoid collisions in
        // TreeSet
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
        Mono<Node<S>> rootInit = Mono.empty();
        if (sortedNodes.isEmpty()) {
            rootInit = initRoot().map(Node::new).doOnNext(sortedNodes::add);
        }
        return rootInit
                .thenMany(evolve(nextParent()).doOnNext(this::backpropagate).take(iterations))
                .then();
    }

    protected Node<S> nextParent() {
        synchronized (sortedNodes) {
            int N_total = sortedNodes.stream().mapToInt(n -> n.visits).sum();
            if (N_total < 1)
                N_total = 1;

            // 2) select u* = argmax of PUCT value (computing rank scores on-the-fly)
            Node<S> selected = null;
            double bestVal = Double.NEGATIVE_INFINITY;
            double pFlat = 1.0 / sortedNodes.size(); // flat prior
            int n = sortedNodes.size();
            int idx = 0;

            for (Node<S> u : sortedNodes) {
                idx++;
                // Compute rank score: RankScore_T(u) = (Rank_T(u) - 1) / (|T| - 1)
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
            Node<S> anc = node.parent;
            while (anc != null) {
                anc.visits += 1;
                anc = anc.parent;
            }
        }
    }

    protected abstract Flux<Node<S>> evolve(Node<S> root);

    protected abstract Mono<S> initRoot();

    /** Return the node considered best by the user comparator (largest element). */
    public Node<S> getBestByComparator() {
        if (sortedNodes.isEmpty())
            return null;
        return sortedNodes.last(); // since comparator is ascending, last is largest/best
    }
}
