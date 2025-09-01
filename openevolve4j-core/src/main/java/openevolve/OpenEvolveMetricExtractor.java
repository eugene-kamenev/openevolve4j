package openevolve;

import java.util.List;
import java.util.function.Function;
import openevolve.mapelites.Repository.Solution;

public class OpenEvolveMetricExtractor
		implements Function<Solution<EvolveSolution>, double[]> {

	private final List<String> metricNames;
	private final boolean[] maximize;

	public OpenEvolveMetricExtractor(List<String> metricNames, boolean[] maximize) {
		this.metricNames = metricNames;
		this.maximize = maximize;
	}

	@Override
	public double[] apply(Solution<EvolveSolution> solution) {
		double[] result = new double[maximize.length];
		for (int i = 0; i < result.length; i++) {
			var defaultValue =
					maximize[i] ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
			result[i] = getValue(solution.fitness().get(metricNames.get(i)), defaultValue);
		}
		return result;
	}

	private static double getValue(Object value, double defaultValue) {
		if (value instanceof Number) {
			return ((Number) value).doubleValue();
		}
		return defaultValue;
	}
}
