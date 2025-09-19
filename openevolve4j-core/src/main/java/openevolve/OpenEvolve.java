package openevolve;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.openai.api.OpenAiApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import openevolve.mapelites.DefaultPopulation;
import openevolve.mapelites.listener.RepositoryListener;
import openevolve.mapelites.MAPElites;
import openevolve.mapelites.Migration;
import openevolve.mapelites.FeatureScaler.ScaleMethod;
import openevolve.util.SolutionUtil;
import openevolve.util.Util;

public class OpenEvolve {

	public static MAPElites<EvolveSolution> create(OpenEvolveConfig config, ObjectMapper mapper,
			List<EvolveSolution> initialSolutions, OpenAiApi openAiApi,
			List<RepositoryListener<EvolveSolution>> listeners) {
		var selConf = config.selection();
		var random = selConf.random();
		var bins = config.mapelites().bins();
		var repository =
				new DefaultPopulation<>(config.comparator(), config.repository().populationSize(),
						config.repository().archiveSize(), config.repository().islands());
		var structure = SolutionUtil.initPath(config.solution().filePattern(), null,
				config.solution().path());
		var initial = new ArrayList<EvolveSolution>();
		if (initialSolutions != null && !initialSolutions.isEmpty()) {
			initial.addAll(initialSolutions);
		} else {
			initial.add(new EvolveSolution(structure.target(), null, Map.of(),
					Map.of("fullRewrite", config.solution().fullRewrite())));
		}
		if (listeners != null && !listeners.isEmpty()) {
			for (var l : listeners) {
				repository.addListener(l);
			}
		}
		var migration = new Migration<>(config.migration().interval(), config.migration().rate(),
				repository);
		var evaluator = new OpenEvolveEvaluator(config.solution().runner(),
				config.solution().path(), structure.linked(), config.metrics().keySet(),
				config.solution().evalTimeout(), mapper);
		var evolveFunction = new OpenEvolveFunction(repository,
				new OpenEvolveAgent(config.prompts(),
						new LLMEnsemble(random, config.llm(), openAiApi), random),
				config.selection().numberDiverse(), config.selection().numberTop());
		var selection = new OpenEvolveSelection(repository, random, selConf.explorationRatio(),
				selConf.exploitationRatio(), selConf.eliteSelectionRatio(),
				selConf.numInspirations(), bins);
		var diversityFunc = new DiversityFunction(repository, 20, 1000, random);
		var complexityFunc = new ComplexityFunction();
		var mapelites = new MAPElites<>(repository, migration, evaluator, evolveFunction,
				() -> initial, selection, config.stopCondition(), ScaleMethod.MIN_MAX,
				config.mapelites().dimensions(), bins) {
			@Override
			protected double getFeatureValue(String feature, EvolveSolution evolved,
					Map<String, Object> fitness) {
				if (feature.equals(Constants.DIVERSITY)) {
					return diversityFunc.applyAsDouble(evolved);
				} else if (feature.equals(Constants.COMPLEXITY)) {
					return complexityFunc.applyAsDouble(evolved);
				} else if (feature.equals(Constants.SCORE)) {
					return Util.getAvgScore(fitness);
				}
				return super.getFeatureValue(feature, evolved, fitness);
			}
		};
		return mapelites;
	}
}
