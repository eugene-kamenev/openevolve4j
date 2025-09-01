package openevolve.unit.mapelites;

import org.junit.jupiter.api.Test;
import openevolve.mapelites.FeatureScaler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import java.util.Arrays;

@DisplayName("FeatureScaler Unit Tests")
class FeatureScalerTest {
    
    private static final int NUM_RANDOM_VALUES = 1000;
    private static final double EPSILON = 1e-10;
    private Random random;
    private double[] testData;
    
    @BeforeEach
    void setUp() {
        random = new Random(42); // Fixed seed for reproducible tests
        testData = generateRandomData();
    }
    
    private double[] generateRandomData() {
        double[] data = new double[NUM_RANDOM_VALUES];
        for (int i = 0; i < NUM_RANDOM_VALUES; i++) {
            // Generate diverse range of values
            if (i % 100 == 0) {
                data[i] = random.nextGaussian() * 1000; // Large values
            } else if (i % 50 == 0) {
                data[i] = random.nextGaussian() * 0.001; // Small values
            } else {
                data[i] = random.nextGaussian() * 10; // Normal range
            }
        }
        return data;
    }
    
    @Test
    @DisplayName("Test MIN_MAX scaling bounds")
    void testMinMaxScaling() {
        FeatureScaler scaler = buildScalerWithData(FeatureScaler.ScaleMethod.MIN_MAX);
        
        // Test min value scales to 0
        double minScaled = scaler.scaled(Arrays.stream(testData).min().orElse(0));
        assertEquals(0.0, minScaled, EPSILON, "Min value should scale to 0");
        
        // Test max value scales to 1
        double maxScaled = scaler.scaled(Arrays.stream(testData).max().orElse(0));
        assertEquals(1.0, maxScaled, EPSILON, "Max value should scale to 1");
    }
    
    @Test
    @DisplayName("Test edge cases: NaN, Infinity, and zero variance")
    void testEdgeCases() {
        FeatureScaler scaler = new FeatureScaler();
        
        // Test NaN and infinity handling
        assertEquals(Double.NaN, scaler.scaleMinMax(Double.NaN), "NaN should not scale");
        assertEquals(Double.NaN, scaler.scaleMinMax(Double.POSITIVE_INFINITY), "Positive infinity should not scale");
        assertEquals(Double.NaN, scaler.scaleMinMax(Double.NEGATIVE_INFINITY), "Negative infinity should not scale");

        // Test identical values (zero variance)
        FeatureScaler identicalScaler = new FeatureScaler(0.0, 0.0, 0.0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0, 0.0, 0.0, 0.0, FeatureScaler.ScaleMethod.MIN_MAX);
        for (int i = 0; i < 10; i++) {
            identicalScaler = identicalScaler.apply(5.0);
        }
        assertEquals(0.5, identicalScaler.scaled(5.0), "Identical values should scale to 0.5");
    }
    
    @Test
    @DisplayName("Test scaler statistics computation")
    void testScalerStatistics() {
        FeatureScaler scaler = buildScalerWithData(FeatureScaler.ScaleMethod.MIN_MAX);
        
        // Verify statistics are reasonable
        assertEquals(NUM_RANDOM_VALUES, scaler.n(), "Should have processed all values");
        assertFalse(Double.isNaN(scaler.mean()), "Mean should not be NaN");
        assertFalse(Double.isNaN(scaler.stdDev()), "StdDev should not be NaN");
        assertFalse(Double.isNaN(scaler.variance()), "Variance should not be NaN");
        assertTrue(scaler.stdDev() >= 0, "StdDev should be non-negative");
        assertTrue(scaler.variance() >= 0, "Variance should be non-negative");
        assertTrue(scaler.max() >= scaler.min(), "Max should be >= min");
    }
    
    @Test
    @DisplayName("Test applying NaN and infinity values")
    void testApplyWithNaNAndInfinity() {
        FeatureScaler scaler = new FeatureScaler();
        
        // Apply NaN and infinity values
        scaler = scaler.apply(Double.NaN);
        scaler = scaler.apply(Double.POSITIVE_INFINITY);
        scaler = scaler.apply(Double.NEGATIVE_INFINITY);
        scaler = scaler.apply(10.0);
        
        // Statistics should still be valid
        assertFalse(Double.isNaN(scaler.mean()), "Mean should not be NaN after applying NaN/infinity");
        assertFalse(Double.isNaN(scaler.stdDev()), "StdDev should not be NaN after applying NaN/infinity");
        assertFalse(Double.isInfinite(scaler.mean()), "Mean should not be infinite after applying NaN/infinity");
    }
    
    @Test
    @DisplayName("Test ROBUST scaling with outliers")
    void testRobustScaling() {
        FeatureScaler scaler = buildScalerWithData(FeatureScaler.ScaleMethod.ROBUST);
        
        // Add some outliers and verify robust scaling handles them
        scaler = scaler.apply(scaler.max() * 10); // Extreme outlier
        
        double outlierScaled = scaler.scaled(scaler.max() * 10);
        assertTrue(outlierScaled >= 0.0 && outlierScaled <= 1.0,
                  "Robust scaling should handle outliers within [0,1]");
    }
    
    @Test
    @DisplayName("Test QUANTILE_UNIFORM scaling")
    void testQuantileUniformScaling() {
        FeatureScaler scaler = buildScalerWithData(FeatureScaler.ScaleMethod.QUANTILE_UNIFORM);
        
        // Test that extreme values are mapped appropriately
        double veryLowValue = scaler.mean() - 3 * scaler.stdDev();
        double veryHighValue = scaler.mean() + 3 * scaler.stdDev();
        
        double lowScaled = scaler.scaled(veryLowValue);
        double highScaled = scaler.scaled(veryHighValue);
        
        assertTrue(lowScaled < 0.1, "Very low value should scale to near 0");
        assertTrue(highScaled > 0.9, "Very high value should scale to near 1");
    }
    
    @Test
    @DisplayName("Test scaling consistency and monotonicity")
    void testScalingConsistency() {
        FeatureScaler scaler = buildScalerWithData(FeatureScaler.ScaleMethod.MIN_MAX);
        
        // Test that scaling the same value multiple times gives same result
        double testValue = testData[random.nextInt(testData.length)];
        double scaled1 = scaler.scaled(testValue);
        double scaled2 = scaler.scaled(testValue);
        assertEquals(scaled1, scaled2, EPSILON, "Scaling should be consistent");
        
        // Test monotonicity: if x1 < x2, then scaled(x1) <= scaled(x2)
        double[] sortedData = Arrays.stream(testData).sorted().toArray();
        for (int i = 0; i < sortedData.length - 1; i++) {
            double scaled1Val = scaler.scaled(sortedData[i]);
            double scaled2Val = scaler.scaled(sortedData[i + 1]);
            assertTrue(scaled1Val <= scaled2Val + EPSILON,
                      "Scaling should preserve order: " + scaled1Val + " <= " + scaled2Val);
        }
    }
    
    @Test
    @DisplayName("Test outlier handling quality across methods")
    void testOutlierHandlingQuality() {
        FeatureScaler baseScaler = buildScalerWithData(FeatureScaler.ScaleMethod.MIN_MAX);
        
        // Add extreme outliers
        double[] dataWithOutliers = Arrays.copyOf(testData, testData.length + 4);
        double mean = baseScaler.mean();
        double std = baseScaler.stdDev();
        dataWithOutliers[testData.length] = mean + 10 * std;     // Extreme positive outlier
        dataWithOutliers[testData.length + 1] = mean - 10 * std; // Extreme negative outlier
        dataWithOutliers[testData.length + 2] = mean + 5 * std;  // Moderate positive outlier
        dataWithOutliers[testData.length + 3] = mean - 5 * std;  // Moderate negative outlier
        
        // Test different methods' robustness to outliers
        for (FeatureScaler.ScaleMethod method : FeatureScaler.ScaleMethod.values()) {
            if (method == FeatureScaler.ScaleMethod.NO_SCALE) continue;
            
            FeatureScaler scalerWithOutliers = buildScalerWithArray(dataWithOutliers, method);
            
            // Calculate how much the main data distribution is affected
            double[] mainDataScaled = Arrays.stream(testData)
                    .map(scalerWithOutliers::scaled)
                    .toArray();
            
            double mainDataRange = Arrays.stream(mainDataScaled).max().orElse(1.0) -
                                  Arrays.stream(mainDataScaled).min().orElse(0.0);
            
            // Robust methods should preserve more range for main data
            if (method == FeatureScaler.ScaleMethod.ROBUST) {
                assertTrue(mainDataRange > 0.3,
                          "ROBUST method should preserve main data range, got: " + mainDataRange);
            }
        }
    }
    
    @Test
    @DisplayName("Test scaling effectiveness across all methods")
    void testScalingEffectiveness() {
        for (FeatureScaler.ScaleMethod method : FeatureScaler.ScaleMethod.values()) {
            if (method == FeatureScaler.ScaleMethod.NO_SCALE) continue;
            
            FeatureScaler scaler = buildScalerWithData(method);
            double[] scaledData = Arrays.stream(testData)
                    .map(scaler::scaled)
                    .toArray();
            
            // Test that scaling actually changes the data scale
            double scaledRange = Arrays.stream(scaledData).max().orElse(1.0) -
                                Arrays.stream(scaledData).min().orElse(0.0);
            
            // Scaled range should be normalized (close to 1.0 for most methods)
                assertTrue(scaledRange <= 1.0 + EPSILON,
                          "Scaled range should be <= 1.0 for " + method + ", got: " + scaledRange);
            
            // Test that extreme values are handled properly
            double[] extremeValues = {scaler.min(), scaler.max(),
                                     scaler.mean() - 3 * scaler.stdDev(),
                                     scaler.mean() + 3 * scaler.stdDev()};
            
            for (double extreme : extremeValues) {
                double scaledExtreme = scaler.scaled(extreme);
                assertTrue(scaledExtreme >= -0.1 && scaledExtreme <= 1.1,
                          "Extreme value should be reasonably scaled for " + method +
                          ", value: " + extreme + ", scaled: " + scaledExtreme);
            }
        }
    }
    
    @Test
    @DisplayName("Test distribution preservation")
    void testDistributionPreservation() {
        // Create data with known distribution properties
        double[] uniformData = new double[1000];
        for (int i = 0; i < uniformData.length; i++) {
            uniformData[i] = i; // Uniform distribution
        }
        
        FeatureScaler uniformScaler = buildScalerWithArray(uniformData, FeatureScaler.ScaleMethod.MIN_MAX);
        double[] scaledUniform = Arrays.stream(uniformData)
                .map(uniformScaler::scaled)
                .toArray();
        
        // Check that uniform distribution remains uniform after scaling
        double uniformVariance = calculateVariance(scaledUniform);
        double expectedUniformVariance = 1.0 / 12.0; // Theoretical variance of uniform [0,1]
        assertEquals(expectedUniformVariance, uniformVariance, 0.01,
                    "MIN_MAX scaling should preserve uniform distribution variance");
    }

    @Test
    @DisplayName("Test NO_SCALE method")
    void testNoScaleMethod() {
        FeatureScaler scaler = buildScalerWithData(FeatureScaler.ScaleMethod.NO_SCALE);
        
        // NO_SCALE should return values unchanged
        for (double value : testData) {
            assertEquals(value, scaler.scaled(value), EPSILON,
                        "NO_SCALE should return original values");
        }
    }

    @Test
    @DisplayName("Test constructor with all parameters")
    void testFullConstructor() {
        FeatureScaler scaler = new FeatureScaler(
            1.0, 2.0, 0.5, -5.0, 10.0, 100, 3.0, 2.0, 1.5,
            FeatureScaler.ScaleMethod.MIN_MAX);
        
        assertEquals(1.0, scaler.mean(), EPSILON);
        assertEquals(2.0, scaler.stdDev(), EPSILON);
        assertEquals(0.5, scaler.variance(), EPSILON);
        assertEquals(10.0, scaler.max(), EPSILON);
        assertEquals(-5.0, scaler.min(), EPSILON);
        assertEquals(100, scaler.n());
        assertEquals(3.0, scaler.K(), EPSILON);
        assertEquals(2.0, scaler.Ex(), EPSILON);
        assertEquals(1.5, scaler.Ex2(), EPSILON);
    }

    @Test
    @DisplayName("Test empty scaler behavior")
    void testEmptyScaler() {
        FeatureScaler emptyScaler = new FeatureScaler();
        
        assertEquals(0, emptyScaler.n());
        assertEquals(0.0, emptyScaler.mean(), EPSILON);
        assertEquals(0.0, emptyScaler.variance(), EPSILON);
        assertEquals(0.0, emptyScaler.stdDev(), EPSILON);
        
        // Scaling with empty scaler should return 0.5 (default)
        assertEquals(0.5, emptyScaler.scaled(42.0), EPSILON);
    }
    
    // Helper methods for quality metrics
    private double calculateVariance(double[] data) {
        double mean = Arrays.stream(data).average().orElse(0.0);
        return Arrays.stream(data)
                .map(x -> Math.pow(x - mean, 2))
                .average()
                .orElse(0.0);
    }
    
    private FeatureScaler buildScalerWithArray(double[] data, FeatureScaler.ScaleMethod method) {
        FeatureScaler scaler = new FeatureScaler(0.0, 0.0, 0.0, Double.POSITIVE_INFINITY,
                                                Double.NEGATIVE_INFINITY, 0, 0.0, 0.0, 0.0, method);
        for (double value : data) {
            scaler = scaler.apply(value);
        }
        return scaler;
    }
    
    private FeatureScaler buildScalerWithData(FeatureScaler.ScaleMethod method) {
        FeatureScaler scaler = new FeatureScaler(0.0, 0.0, 0.0, Double.POSITIVE_INFINITY,
                                                Double.NEGATIVE_INFINITY, 0, 0.0, 0.0, 0.0, method);
        for (double value : testData) {
            scaler = scaler.apply(value);
        }
        return scaler;
    }
}
