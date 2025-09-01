package openevolve;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.commons.lang3.RandomStringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import openevolve.mapelites.DefaultRepository;
import openevolve.mapelites.MAPElites;
import openevolve.mapelites.Migration;
import openevolve.mapelites.FeatureScaler.ScaleMethod;
import openevolve.util.Util;

public class OpenEvolve {

	public static MAPElites<EvolveSolution> create(OpenEvolveConfig config, ObjectMapper mapper) {
		var selConf = config.selection();
		var random = selConf.random();
		var bins = config.mapelites().bins();
		var repository =
				new DefaultRepository<>(config.comparator(), config.repository().populationSize(),
						config.repository().archiveSize(), config.repository().islands());
		var migration = new Migration<>(config.migration().interval(), config.migration().rate(),
				repository);
		var evaluator = new OpenEvolveEvaluator(config.solution().runner(),
				config.metrics().keySet(), config.solution().evalTimeout(), mapper);
		var evolveFunction = new OpenEvolveFunction(repository,
				new OpenEvolveAgent(config.prompts(), new LLMEnsemble(random, config.llm()),
						random, config.selection().numberTop(), config.selection().numberDiverse()),
				config.selection().numberDiverse(), config.selection().numberTop());
		var selection = new OpenEvolveSelection(repository, random, selConf.explorationRatio(),
				selConf.exploitationRatio(), selConf.eliteSelectionRatio(),
				selConf.numInspirations(), bins);
		var diversityFunc =
				new DiversityFunction(repository, 20,
						1000, random);
		var complexityFunc = new ComplexityFunction();
		var code = Code.fromPath(config.solution().path(), config.solution().filePattern());
		var randString = RandomStringUtils.secure().next(8, true, true);
		var initialPath = config.solution().path().getParent().resolve("solutions").resolve(randString);
		Supplier<List<EvolveSolution>> initial = () -> List
				.of(new EvolveSolution(null, config.solution().path(), initialPath, code.code(),
						config.solution().language(), null, Map.of(), config.solution().fullRewrite()));
		var mapelites = new MAPElites<>(repository, migration, evaluator, evolveFunction, initial,
				selection, config.stopCondition(), ScaleMethod.MIN_MAX,
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
		mapelites.addListener(new OpenEvolveCheckpointListener(config.solution().path().getParent().resolve("ckpt"), 10, mapper, repository, null));
		return mapelites;
	}
}
