package openevolve;

import java.util.function.ToDoubleFunction;

public class ComplexityFunction implements ToDoubleFunction<EvolveSolution> {
	@Override
	public double applyAsDouble(EvolveSolution value) {
		var content = value.content();
		return content == null ? 0.0 : content.length();
	}
}
