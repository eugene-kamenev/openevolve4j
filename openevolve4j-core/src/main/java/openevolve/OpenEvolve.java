package openevolve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.api.OpenAiApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import openevolve.agents.DeepResearchAgent;
import openevolve.agents.OpenEvolveAgent;
import openevolve.deepresearch.tools.TavilyWebSearch;
import openevolve.deepresearch.tools.WebFetch;
import openevolve.deepresearch.tools.WebSearchAndFetch;
import openevolve.domain.PuctTreeConfig;
import openevolve.domain.SourceTree;
import openevolve.tree.PuctTree;
import openevolve.tree.PuctTree.Repository;
import openevolve.util.Evaluator;
import openevolve.util.SourceTreeUtil;
import openevolve.util.Util;

public class OpenEvolve {

	public static DeepResearchAgent create(ChatClient chatClient,
			Map<String, List<PromptTemplate>> prompts, int maxTopics, int maxSearchPhrases) {
		return new DeepResearchAgent(new WebSearchAndFetch(new TavilyWebSearch(null), WebFetch.NONE),
				chatClient, prompts, maxTopics, maxSearchPhrases, new Random(42));
	}

	public static PuctTree<SourceTree> create(PuctTreeConfig config, OpenAiApi openAiApi,
			Repository<SourceTree> manager, ObjectMapper mapper) {
		var structure = SourceTreeUtil.initPath(config.solution().filePattern(), null,
				config.solution().path());
		Supplier<SourceTree> rootSupplier = () -> new SourceTree(structure.target(), Map.of("fullRewrite", config.solution().fullRewrite()));
		var llm = config.llm().models();
		var agents = new ArrayList<OpenEvolveAgent>();
		for (var model : llm) {
			var client = Util.newChatClient(model, openAiApi);
			agents.add(new OpenEvolveAgent(config.prompts(), client, model.getModel()));
		}
		var evaluator = new Evaluator(config.solution().runner(),
				config.solution().path(), structure.linked(), config.metrics().keySet(),
				config.solution().evalTimeout(), mapper);
		return new PuctTree<>(config.explorationConstant(), config.comparator(), manager, rootSupplier,
				agents, evaluator);
	}
}
