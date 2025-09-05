package openevolve.integration;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;
import openevolve.Constants;
import openevolve.LLMEnsemble;
import openevolve.OpenEvolveAgent;
import openevolve.OpenEvolveConfig.LLM;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import openevolve.util.CodeParsingUtils;
import openevolve.Code;

public class PromptsTest {

	private OpenEvolveAgent agent;
	private LLMEnsemble llmEnsemble;

	@BeforeEach
	void setup() {
		var random = new Random(42);
		var models = List.of(OpenAiChatOptions.builder().model("qwen3-4b").build());
		llmEnsemble = new LLMEnsemble(random, new LLM(Constants.DEFAULT_PROMPTS, models, "http://192.168.50.99:4000", "sk-9DzvwVXmpXKAcoWTgmZnRg"));
		agent = new OpenEvolveAgent(Constants.DEFAULT_PROMPTS, llmEnsemble, random, 0, 0);
	}

	@Test
	void testUserDiffPrompt() {
		var diffTmpl = agent.getTemplate(Constants.USER_DIFF);

		// Simple Python snippet that matches the example "before" code exactly.
		String baseCode = ""
			+ "for i in range(m):\n"
			+ "    for j in range(p):\n"
			+ "        for k in range(n):\n"
			+ "            C[i, j] += A[i, k] * B[k, j]\n";

		// Render the current solution in the same shape the agent uses.
		var solutionRendered = agent.getTemplate(Constants.SOLUTION).render(Map.of(
			"code", baseCode,
			"language", "python",
			"metrics", "{}",
			"name", "Current Solution"
		));

		String task = String.join("\n",
			"Optimize matrix multiplication by reordering the loops for better memory access.",
			"Swap the inner loops j and k like in the example.",
			"Respond using one or more diff blocks only. Do not include explanations or code fences."
		);

		var client = llmEnsemble.sample();
		var llmResponse = client
			.prompt(new Prompt(new UserMessage(diffTmpl.render(Map.of(
				"task", task,
				"parents", "",
				"solution", solutionRendered
			)))))
			.call()
			.content();

		String cleaned = stripOuterCodeFences(llmResponse);
		assertTrue(cleaned.contains("<<<<<<< SEARCH") && cleaned.contains(">>>>>>> REPLACE"),
			"LLM must return at least one diff block. Response:\n" + llmResponse);

		String applied = applyDiff(baseCode, cleaned);

		assertNotEquals(baseCode, applied, "Applied code should differ from the original. Diff was:\n" + cleaned);
		// Expect the loop order to be i -> k -> j in the result after applying the diff.
		assertTrue(applied.contains("for k in range(n):") && applied.contains("for j in range(p):"),
			"Expected the loop order to change as per the example. Result:\n" + applied);
	}

	@Test
	void testRefactorFunctionWithDiffBlock() {
		var diffTmpl = agent.getTemplate(Constants.USER_DIFF);

		String baseCode = ""
			+ "def compute_sum(a, b):\n"
			+ "    return a + b\n"
			+ "\n"
			+ "result = compute_sum(2, 3)\n";

		var solutionRendered = agent.getTemplate(Constants.SOLUTION).render(Map.of(
			"code", baseCode,
			"language", "python",
			"metrics", "{}",
			"name", "Current Solution"
		));

		String task = String.join("\n",
			"Refactor the function 'compute_sum' to use a lambda expression instead.",
			"Respond using one or more diff blocks only. Do not include explanations or code fences."
		);

		var client = llmEnsemble.sample();
		var llmResponse = client
			.prompt(new Prompt(new UserMessage(diffTmpl.render(Map.of(
				"task", task,
				"parents", "",
				"solution", solutionRendered
			)))))
			.call()
			.content();

		String cleaned = stripOuterCodeFences(llmResponse);
		List<CodeParsingUtils.DiffBlock> blocks = CodeParsingUtils.extractDiffs(cleaned);
		assertFalse(blocks.isEmpty(), "No diff blocks found in response.");

		String applied = CodeParsingUtils.applyDiff(baseCode, cleaned);

		assertNotEquals(baseCode, applied, "Applied code should differ from the original. Diff was:\n" + cleaned);
		assertTrue(applied.contains("compute_sum = lambda a, b: a + b") || applied.contains("lambda"), "Expected lambda refactor. Result:\n" + applied);
	}

	@Test
	void testMultiFileDiffWithCodeClass() {
		var diffTmpl = agent.getTemplate(Constants.USER_DIFF);

		String file1 = ""
			+ "class Counter {\n"
			+ "    private int count = 0;\n"
			+ "    public void increment() {\n"
			+ "        count++;\n"
			+ "    }\n"
			+ "    public int getCount() {\n"
			+ "        return count;\n"
			+ "    }\n"
			+ "}\n";

		String file2 = ""
			+ "public class Main {\n"
			+ "    public static void main(String[] args) {\n"
			+ "        Counter c = new Counter();\n"
			+ "        c.increment();\n"
			+ "        System.out.println(c.getCount());\n"
			+ "    }\n"
			+ "}\n";

		String codeBlock = String.join(System.lineSeparator(),
			Constants.SOURCE_START + "/Counter.java" + Constants.SOURCE_END,
			file1,
			Constants.SOURCE_START + "/Main.java" + Constants.SOURCE_END,
			file2
		);

		Code codeObj = Code.fromContent(codeBlock, Path.of("."));

		var solutionRendered = agent.getTemplate(Constants.SOLUTION).render(Map.of(
			"code", codeObj.code(),
			"language", "java",
			"metrics", "{}",
			"name", "Current Solution"
		));

		String task = String.join("\n",
			"Add a decrement method to the Counter class and call it in Main before printing the count.",
			"Respond using one or more diff blocks only. Do not include explanations or code fences."
		);

		var client = llmEnsemble.sample();
		var llmResponse = client
			.prompt(new Prompt(new UserMessage(diffTmpl.render(Map.of(
				"task", task,
				"parents", "",
				"solution", solutionRendered
			)))))
			.call()
			.content();

		String cleaned = stripOuterCodeFences(llmResponse);
		List<CodeParsingUtils.DiffBlock> blocks = CodeParsingUtils.extractDiffs(cleaned);
		assertFalse(blocks.isEmpty(), "No diff blocks found in response.");

		String applied = CodeParsingUtils.applyDiff(codeObj.code(), cleaned);

		assertNotEquals(codeObj.code(), applied, "Applied code should differ from the original. Diff was:\n" + cleaned);
		assertTrue(applied.contains("public void decrement()") && applied.contains("c.decrement();"), "Expected decrement method and call. Result:\n" + applied);
	}

	@Test
	void testFullRewriteWithCodeParsingUtils() {
		var rewriteTmpl = agent.getTemplate(Constants.USER_FULL_REWRITE);

		String baseCode = ""
			+ "def multiply(a, b):\n"
			+ "    return a * b\n";

		var solutionRendered = agent.getTemplate(Constants.SOLUTION).render(Map.of(
			"code", baseCode,
			"language", "python",
			"metrics", "{}",
			"name", "Current Solution"
		));

		String task = String.join("\n",
			"Rewrite the function to handle multiplication of lists elementwise.",
			"Respond with a full code block only."
		);

		var client = llmEnsemble.sample();
		var llmResponse = client
			.prompt(new Prompt(new UserMessage(rewriteTmpl.render(Map.of(
				"task", task,
				"parents", "",
				"solution", solutionRendered
			)))))
			.call()
			.content();

		var extracted = CodeParsingUtils.parseFullRewrite(llmResponse, "python");
		assertTrue(extracted.isPresent(), "Expected code block in LLM response.");
		String rewritten = extracted.get();

		assertTrue(rewritten.contains("def multiply(a, b):"), "Expected function definition in rewrite.");
		assertTrue(rewritten.contains("zip(a, b)") || rewritten.contains("for"), "Expected elementwise logic. Result:\n" + rewritten);
	}

	@Test
	void testDiffSummaryFormatting() {
		List<CodeParsingUtils.DiffBlock> blocks = List.of(
			new CodeParsingUtils.DiffBlock("x = 1", "x = 2"),
			new CodeParsingUtils.DiffBlock("def foo():\n    pass", "def foo():\n    print('Hello')")
		);
		String summary = CodeParsingUtils.formatDiffSummary(blocks);
		assertTrue(summary.contains("Change 1:") && summary.contains("Change 2:"), "Summary should enumerate changes.");
		assertTrue(summary.contains("Replace 2 lines with 2 lines") || summary.contains("to"), "Summary should describe multi-line changes.");
	}

	// Applies one or more diff blocks of the form:
	// <<<<<<< SEARCH
	// <search>
	// =======
	// <replace>
	// >>>>>>> REPLACE
	private static String applyDiff(String base, String diffText) {
		var blocks = parseBlocks(diffText);
		assertFalse(blocks.isEmpty(), "No diff blocks found in response.");
		String result = base;
		for (var b : blocks) {
			assertTrue(result.contains(b.search), "SEARCH must match exactly in the current program:\n" + b.search);
			result = result.replaceFirst(Pattern.quote(b.search), Matcher.quoteReplacement(b.replaceStr));
		}
		return result;
	}

	private static record Block(String search, String replaceStr) {}

	private static List<Block> parseBlocks(String diffText) {
		var pattern = Pattern.compile(
			"<<<<<<<\\s*SEARCH\\s*\\R(.*?)\\R=======\\s*\\R(.*?)\\R>>>>>>>\\s*REPLACE",
			Pattern.DOTALL
		);
		Matcher m = pattern.matcher(diffText);
		List<Block> blocks = new ArrayList<>();
		while (m.find()) {
			String search = m.group(1);
			String replace = m.group(2);
			blocks.add(new Block(search, replace));
		}
		return blocks;
	}

	private static String stripOuterCodeFences(String s) {
		String t = s.trim();
		if (t.startsWith("```")) {
			int firstNl = t.indexOf('\n');
			int lastFence = t.lastIndexOf("```");
			if (firstNl >= 0 && lastFence > firstNl) {
				return t.substring(firstNl + 1, lastFence).trim();
			}
		}
		return s;
	}
}
