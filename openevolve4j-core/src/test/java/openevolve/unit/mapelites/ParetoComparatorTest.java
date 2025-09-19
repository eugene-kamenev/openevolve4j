package openevolve.unit.mapelites;

import org.junit.jupiter.api.Test;
import openevolve.mapelites.ParetoComparator;
import openevolve.mapelites.Population;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ParetoComparator Unit Tests")
public class ParetoComparatorTest {

    private Population.Solution<String> sol(double[] objectives) {
        Map<String, Object> fitness = new HashMap<>();
        // expose objectives as an array under key "objs"
        fitness.put("objs", objectives);
        return new Population.Solution<>(UUID.randomUUID(), "s", null, fitness, 0, 0, new int[]{0});
    }

    @Test
    @DisplayName("Test dominance and lexicographic tie breaking")
    public void testDominanceAndLexicographicTieBreak() {
        // We want to maximize both objectives
        boolean[] maximize = new boolean[]{true, true};
        ParetoComparator<String> cmp = new ParetoComparator<>(maximize, s -> (double[]) s.fitness().get("objs"));

        var a = sol(new double[]{2.0, 1.0});
        var b = sol(new double[]{1.0, 2.0});
        var c = sol(new double[]{2.0, 2.0});
        var d = sol(new double[]{2.0, 1.0});

        // c dominates both a and b
        assertTrue(cmp.dominates(c, a));
        assertTrue(cmp.dominates(c, b));

        // a and b are non-dominated relative to each other; comparator should use lexicographic tiebreak
        int ab = cmp.compare(a, b);
        int ba = cmp.compare(b, a);
        assertEquals(-ab, ba);

        // identical objectives compare equal
        assertEquals(0, cmp.compare(a, d));
    }

    @Test
    @DisplayName("Test mixed minimization and maximization objectives")
    public void testMinimizationAndMaximizationMix() {
        // first objective minimize, second maximize
        boolean[] maximize = new boolean[]{false, true};
        ParetoComparator<String> cmp = new ParetoComparator<>(maximize, s -> (double[]) s.fitness().get("objs"));

        var a = sol(new double[]{1.0, 5.0}); // better first (smaller), good second
        var b = sol(new double[]{2.0, 10.0}); // worse first, better second

        // Neither strictly dominates the other, lexicographic should determine order: compare first objective (minimize)
        int res = cmp.compare(a, b);
        assertTrue(res > 0);
    }

    @Test
    @DisplayName("Test edge cases with equal objectives")
    public void testEqualObjectives() {
        boolean[] maximize = new boolean[]{true, true};
        ParetoComparator<String> cmp = new ParetoComparator<>(maximize, s -> (double[]) s.fitness().get("objs"));

        var a = sol(new double[]{1.0, 1.0});
        var b = sol(new double[]{1.0, 1.0});

        assertEquals(0, cmp.compare(a, b), "Equal objectives should compare as equal");
        assertFalse(cmp.dominates(a, b), "Equal solutions should not dominate each other");
        assertFalse(cmp.dominates(b, a), "Equal solutions should not dominate each other");
    }

    @Test
    @DisplayName("Test single objective optimization")
    public void testSingleObjective() {
        boolean[] maximize = new boolean[]{true};
        ParetoComparator<String> cmp = new ParetoComparator<>(maximize, s -> new double[]{(Double) s.fitness().get("obj")});

        Map<String, Object> fitness1 = new HashMap<>();
        fitness1.put("obj", 5.0);
        var a = new Population.Solution<>(UUID.randomUUID(), "s1", null, fitness1, 0, 0, new int[]{0});

        Map<String, Object> fitness2 = new HashMap<>();
        fitness2.put("obj", 3.0);
        var b = new Population.Solution<>(UUID.randomUUID(), "s2", null, fitness2, 0, 0, new int[]{0});

        assertTrue(cmp.dominates(a, b), "Higher objective value should dominate lower (maximization)");
        assertFalse(cmp.dominates(b, a), "Lower objective value should not dominate higher");
    }

    @Test
    @DisplayName("Test constructor with null parameters")
    @SuppressWarnings("unused")
    public void testConstructorValidation() {
        assertThrows(NullPointerException.class, () ->
            new ParetoComparator<String>(null, s -> new double[]{1.0}),
            "Should throw when maximize array is null");

        assertThrows(NullPointerException.class, () ->
            new ParetoComparator<String>(new boolean[]{true}, null),
            "Should throw when objective extractor is null");
    }

    @Test
    @DisplayName("Test with NaN and infinite values")
    public void testNaNAndInfiniteValues() {
        boolean[] maximize = new boolean[]{true, true};
        ParetoComparator<String> cmp = new ParetoComparator<>(maximize, s -> (double[]) s.fitness().get("objs"));

        var normal = sol(new double[]{1.0, 1.0});
        var withNaN = sol(new double[]{Double.NaN, 1.0});
        var withInf = sol(new double[]{Double.POSITIVE_INFINITY, 1.0});

        // NaN values should be handled gracefully
        assertDoesNotThrow(() -> cmp.compare(normal, withNaN));
        assertDoesNotThrow(() -> cmp.compare(withNaN, normal));
        assertDoesNotThrow(() -> cmp.dominates(normal, withNaN));

        // Infinite values should be handled gracefully
        assertDoesNotThrow(() -> cmp.compare(normal, withInf));
        assertDoesNotThrow(() -> cmp.compare(withInf, normal));
        assertDoesNotThrow(() -> cmp.dominates(normal, withInf));
    }
}
