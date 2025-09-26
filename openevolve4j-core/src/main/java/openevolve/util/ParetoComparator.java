package openevolve.util;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

public class ParetoComparator implements Comparator<Map<String, Object>> {

    private final boolean[] maximize;
    private final String[] names;

    public ParetoComparator(boolean[] maximize, String[] names) {
        Objects.requireNonNull(maximize, "Maximize array must not be null");
        this.maximize = maximize;
        this.names = names;
    }

    /**
     * Compares two solutions based on Pareto dominance.
     * Returns a positive number if a dominates b, a negative number if b dominates a.
     * For non-dominated pairs, returns 0.
     * Use {@link #dominates} for strict Pareto dominance checks.
     */
    public int compare(Map<String, Object> a, Map<String, Object> b) {
        double[] aObj = extract(a, names, maximize);
        double[] bObj = extract(b, names, maximize);

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

        return 0; // All objectives are equal
    }

    public boolean dominates(Map<String, Object> a, Map<String, Object> b) {
        // Strict Pareto dominance: a is at least as good as b on all objectives
        // and strictly better on at least one objective. Lexicographic tiebreak
        // used in compare() is intentionally ignored here.
        double[] aObj = extract(a, names, maximize);
        double[] bObj = extract(b, names, maximize);

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

	private static double[] extract(Map<String, Object> solution, String[] names, boolean[] maximize) {
		double[] result = new double[maximize.length];
		for (int i = 0; i < result.length; i++) {
			var defaultValue =
					maximize[i] ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
			result[i] = getValue(solution.get(names[i]), defaultValue);
		}
		return result;
	}

	private static double getValue(Object value, double defaultValue) {
		if (value instanceof Number) {
			return ((Number) value).doubleValue();
		} else if (value instanceof String str) {
            if (str.equals("Infinity")) {
                return Double.POSITIVE_INFINITY;
            } else if (str.equals("-Infinity")) {
                return Double.NEGATIVE_INFINITY;
            }
        }
		return defaultValue;
	}
}
