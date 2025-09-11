package openevolve;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import openevolve.mapelites.DefaultRepository;
import openevolve.mapelites.listener.RepositoryListener.SolutionWriter;
import openevolve.mapelites.listener.MAPElitesListener.StateWriter;
import openevolve.mapelites.MAPElites;
import openevolve.mapelites.Migration;
import openevolve.mapelites.FeatureScaler.ScaleMethod;
import openevolve.mapelites.Repository.Solution;
import openevolve.util.SolutionUtil;
import openevolve.util.Util;

public class OpenEvolve {

	public static MAPElites<EvolveSolution> create(OpenEvolveConfig config, ObjectMapper mapper,
			boolean restart, List<EvolveSolution> initialSolutions,
			Map<UUID, Solution<EvolveSolution>> allSolutions, RestClient.Builder restBuilder) {
		var selConf = config.selection();
		var random = selConf.random();
		var bins = config.mapelites().bins();
		var structure = SolutionUtil.initPath(config.solution().filePattern(), null,
				config.solution().path());
		var initial = new ArrayList<EvolveSolution>();
		if (initialSolutions != null && !initialSolutions.isEmpty()) {
			restart = true;
			initial.addAll(initialSolutions);
		} else {
			initial.add(new EvolveSolution(null, Instant.now(), structure.target(),
					config.solution().language(), null, Map.of(), Map.of(),
					config.solution().fullRewrite()));
		}
		var solutionsJson = config.solution().path().getParent().resolve("solutions.jsonl");
		var stateJson = config.solution().path().getParent().resolve("state.json");
		var stateWriter = new StateWriter<EvolveSolution>(stateJson, mapper, restart);
		var repository =
				new DefaultRepository<>(config.comparator(), config.repository().populationSize(),
						config.repository().archiveSize(), config.repository().islands());
		if (stateWriter.getCurrentState() != null) {
			repository.restore(stateWriter.getCurrentState().repository(), allSolutions);
		}
		repository.addListener(new SolutionWriter<>(solutionsJson, mapper));
		var migration = new Migration<>(config.migration().interval(), config.migration().rate(),
				repository);
		var evaluator = new OpenEvolveEvaluator(config.solution().runner(),
				config.solution().path(), structure.linked(), config.metrics().keySet(),
				config.solution().evalTimeout(), mapper);
		var evolveFunction = new OpenEvolveFunction(repository,
				new OpenEvolveAgent(config.prompts(),
						new LLMEnsemble(random, config.llm(), restBuilder), random,
						config.selection().numberTop(), config.selection().numberDiverse()),
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
		mapelites.addListener(stateWriter);
		return mapelites;
	}
}
