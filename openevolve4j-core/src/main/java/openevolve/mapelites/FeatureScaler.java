package openevolve.mapelites;

import java.util.function.BiFunction;
import java.util.function.DoubleFunction;
import java.util.function.ToDoubleBiFunction;

public record FeatureScaler(double mean, double stdDev, double variance, double min, double max,
        int n, double K, double Ex, double Ex2, ScaleMethod scaleMethod) implements DoubleFunction<FeatureScaler> {

    public enum ScaleMethod implements BiFunction<FeatureScaler, Double, Double> {
        MIN_MAX((s, v) -> s.scaleMinMax(v)),
        ROBUST((s, v) -> s.scaleRobust(v)),
        QUANTILE_UNIFORM((s, v) -> s.scaleQuantileUniform(v)),
        NO_SCALE((s, v) -> s.noScale(v));

        private final ToDoubleBiFunction<FeatureScaler, Double> operator;

        ScaleMethod(ToDoubleBiFunction<FeatureScaler, Double> operator) {
            this.operator = operator;
        }

        public Double apply(FeatureScaler stats, Double value) {
            return operator.applyAsDouble(stats, value);
        }
    }

    public FeatureScaler(ScaleMethod method) {
        this(0.0, 0.0, 0.0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0, 0.0, 0.0, 0.0, method);
    }

    public FeatureScaler() {
        this(ScaleMethod.MIN_MAX);
    }

    @Override
    public FeatureScaler apply(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return this;
        }
        var n = this.n;
        var K = this.K;
        var Ex = this.Ex;
        var Ex2 = this.Ex2;
        if (n == 0) {
            K = value;
        }
        var diff = value - K;
        Ex += diff;
        Ex2 += diff * diff;
        n = n + 1;
        var max = Math.max(this.max, value);
        var min = Math.min(this.min, value);
        var mean = K + Ex / n;
        var variance = (Ex2 - Ex * Ex / n) / n;
        var stdDev = Math.sqrt(Math.max(0, variance));
        
        // Guard against NaN/infinite computed values
        if (Double.isNaN(mean) || Double.isInfinite(mean)) {
            mean = 0.0;
        }
        if (Double.isNaN(variance) || Double.isInfinite(variance)) {
            variance = 0.0;
        }
        if (Double.isNaN(stdDev) || Double.isInfinite(stdDev)) {
            stdDev = 0.0;
        }
        
        return new FeatureScaler(mean, stdDev, variance, min, max, n, K, Ex, Ex2, scaleMethod);
    }

    public double scaled(double value) {
        if (scaleMethod != null) {
            return scaleMethod.apply(this, value);
        }
        return scaleMinMax(value);
    }

    public double noScale(double value) {
        return value;
    }

    /**
     * Scales bounded [0, 1]
     *
     * @param value
     * @return
     */
    public double scaleMinMax(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return Double.NaN;
        }
        if (min == max || n < 2) {
            return 0.5;
        }
        return (value - min) / (max - min);
    }

    /**
     * Robust scaling using approximate median and IQR
     * Less sensitive to outliers than min-max scaling
     *
     * @param value
     * @return
     */
    public double scaleRobust(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return Double.NaN;
        }
        if (n < 2) {
            return 0.5;
        }
        // Approximate median as mean for simplicity
        double median = mean;
        // Approximate IQR as 1.35 * stdDev (for normal distribution)
        double iqr = 1.35 * stdDev;
        if (iqr == 0.0) {
            return 0.5;
        }
        // Scale to approximately [-2, 2] then map to [0, 1]
        double scaled = (value - median) / iqr;
        double result = 1.0 / (1.0 + Math.exp(-scaled * 0.5));
        return Double.isNaN(result) || Double.isInfinite(result) ? Double.NaN : result;
    }

    /**
     * Quantile uniform scaling using CDF approximation
     * Maps values based on their rank in the distribution
     *
     * @param value
     * @return
     */
    public double scaleQuantileUniform(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return Double.NaN;
        }
        if (stdDev == 0.0 || n < 2) {
            return 0.5;
        }
        // Approximate CDF using normal distribution assumption
        double zScore = (value - mean) / stdDev;
        // Approximate normal CDF using error function approximation
        double result = 0.5 * (1.0 + Math.tanh(zScore * Math.sqrt(2.0 / Math.PI)));
        return Double.isNaN(result) || Double.isInfinite(result) ? Double.NaN :
               Math.max(0.0, Math.min(1.0, result));
    }
}
