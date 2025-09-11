package openevolve;

import java.util.function.ToDoubleFunction;
import openevolve.util.SolutionUtil;

public class ComplexityFunction implements ToDoubleFunction<EvolveSolution> {
	@Override
	public double applyAsDouble(EvolveSolution value) {
		var content = SolutionUtil.toContent(value.files());
		return content == null ? 0.0 : content.length();
	}
}
