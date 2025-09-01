package openevolve.mapelites;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.Function;
import openevolve.mapelites.Repository.Solution;

public class ParetoComparator<T> implements Comparator<Solution<T>> {

    private final boolean[] maximize;
    private final Function<Solution<T>, double[]> extractor;

    public ParetoComparator(boolean[] maximize, Function<Solution<T>, double[]> extractor) {
        Objects.requireNonNull(maximize, "Maximize array must not be null");
        Objects.requireNonNull(extractor, "Extractor function must not be null");
        this.maximize = maximize;
        this.extractor = extractor;
    }

    /**
     * Compares two solutions based on Pareto dominance.
     * Returns a positive number if a dominates b, a negative number if b dominates a.
     * For non-dominated pairs, applies a lexicographic ordering for deterministic sorting.
     * Note: This lexicographic ordering must NOT be used to infer dominance. Use {@link #dominates}
     * for strict Pareto dominance checks.
     */
    public int compare(Solution<T> a, Solution<T> b) {
        double[] aObj = extractor.apply(a);
        double[] bObj = extractor.apply(b);

        boolean aDominatesB = true;
        boolean bDominatesA = true;
        boolean hasStrictAdvantageA = false;
        boolean hasStrictAdvantageB = false;

        // Check each objective for dominance
        for (int i = 0; i < aObj.length; i++) {
            double valA = aObj[i];
            double valB = bObj[i];
            if (maximize[i]) {
                // For maximization, higher values are better
                if (valA > valB) {
                    bDominatesA = false;
                    hasStrictAdvantageA = true;
                } else if (valA < valB) {
                    aDominatesB = false;
                    hasStrictAdvantageB = true;
                }
            } else {
                // For minimization, lower values are better
                if (valA < valB) {
                    bDominatesA = false;
                    hasStrictAdvantageA = true;
                } else if (valA > valB) {
                    aDominatesB = false;
                    hasStrictAdvantageB = true;
                }
            }
        }

        // Determine dominance based on flags
        if (aDominatesB && hasStrictAdvantageA) {
            return 1;
        } else if (bDominatesA && hasStrictAdvantageB) {
            return -1;
        }

        // Lexicographical ordering for non-dominated solutions
        for (int i = 0; i < aObj.length; i++) {
            double valA = aObj[i];
            double valB = bObj[i];
            int comparison;
            if (maximize[i]) {
                // Prefer higher values -> if a has higher value, return positive
                comparison = Double.compare(valA, valB);
            } else {
                // Prefer lower values -> if a has lower value, return positive
                comparison = Double.compare(valB, valA);
            }
            if (comparison != 0) {
                return comparison;
            }
        }

        return 0; // All objectives are equal
    }

    public boolean dominates(Solution<T> a, Solution<T> b) {
        // Strict Pareto dominance: a is at least as good as b on all objectives
        // and strictly better on at least one objective. Lexicographic tiebreak
        // used in compare() is intentionally ignored here.
        double[] aObj = extractor.apply(a);
        double[] bObj = extractor.apply(b);

        boolean strictlyBetterOnAtLeastOne = false;
        for (int i = 0; i < aObj.length; i++) {
            double valA = aObj[i];
            double valB = bObj[i];
            if (maximize[i]) {
                if (valA < valB) {
                    return false; // worse on a maximization objective
                }
                if (valA > valB) {
                    strictlyBetterOnAtLeastOne = true;
                }
            } else { // minimize
                if (valA > valB) {
                    return false; // worse on a minimization objective
                }
                if (valA < valB) {
                    strictlyBetterOnAtLeastOne = true;
                }
            }
        }
        return strictlyBetterOnAtLeastOne;
    }
}
