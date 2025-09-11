package openevolve;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import org.apache.commons.text.similarity.LevenshteinDistance;
import openevolve.mapelites.Repository;
import openevolve.mapelites.Repository.Solution;
import openevolve.util.SolutionUtil;

/**
 * Calculates diversity scores for evolution solutions using configurable distance functions.
 *
 * <p>This class maintains a cache of diversity calculations and a set of reference solutions
 * for comparison. It supports different diversity metrics including fast approximation and
 * Levenshtein distance.
 *
 * <p><strong>Thread Safety:</strong> This class is not thread-safe and requires external
 * synchronization for concurrent access.
 */
public class DiversityFunction implements ToDoubleFunction<EvolveSolution> {

	private static final MessageDigest MD;

	static {
		try {
			MD = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Failed to initialize MessageDigest", e);
		}
	}

	public record DiversityCache(double value, long timestamp) {
	}

	private final Map<String, DiversityCache> cache = new HashMap<>();
	private final Set<String> diversitySet = new HashSet<>();
	private final ToDoubleBiFunction<String, String> diversityFunc;
	private final int diversitySetSize;
	private final int diversityCacheSize;
	private final Random random;
	private final Repository<EvolveSolution> repository;

	public DiversityFunction(Repository<EvolveSolution> repository, int diversitySetSize,
			int diversityCacheSize, Random random,
			ToDoubleBiFunction<String, String> diversityFunc) {
		this.repository = repository;
		this.diversityFunc = diversityFunc;
		this.diversitySetSize = diversitySetSize;
		this.diversityCacheSize = diversityCacheSize;
		this.random = random;
	}

	public DiversityFunction(Repository<EvolveSolution> repository, int diversitySetSize,
			int diversityCacheSize, Random random) {
		this(repository, diversitySetSize, diversityCacheSize, random, DiversityFunction::fast);
	}

	@Override
	public double applyAsDouble(EvolveSolution t) {
		var content = SolutionUtil.toContent(t.files());
		if (content == null || content.isEmpty()) {
			return 0.0;
		}
		var key = Base64.getEncoder().encodeToString(MD.digest(content.getBytes()));
		if (!cache.containsKey(key)) {
			updateDiversitySet();
			double diversity = this.diversitySet.stream().filter(c -> !c.equals(content))
					.mapToDouble(c -> diversityFunc.applyAsDouble(content, c)).average()
					.orElse(0.0);
			if (cache.size() >= diversityCacheSize) {
				String oldestKey = null;
				long oldestTime = Long.MAX_VALUE;
				for (Map.Entry<String, DiversityCache> entry : cache.entrySet()) {
					if (entry.getValue().timestamp() < oldestTime) {
						oldestTime = entry.getValue().timestamp();
						oldestKey = entry.getKey();
					}
				}
				if (oldestKey != null) {
					cache.remove(oldestKey);
				}
			}
			cache.put(key, new DiversityCache(diversity, System.currentTimeMillis()));
		}
		return cache.get(key).value();
	}

	private void updateDiversitySet() {
		if (this.diversitySet.size() < this.diversitySetSize) {
			if (repository.count() <= this.diversitySetSize) {
				repository.findAll().stream().map(s -> SolutionUtil.toContent(s.solution().files()))
						.forEach(this.diversitySet::add);
			} else {
				// select solutions with maximum diversity
				var remaining = new ArrayList<>(repository.findAll());
				var selected = new ArrayList<Solution<EvolveSolution>>();
				int randIdx = random.nextInt(remaining.size());
				selected.add(remaining.remove(randIdx));
				while (selected.size() < this.diversitySetSize && !remaining.isEmpty()) {
					double maxDiversity = -1;
					int bestIdx = -1;
					for (int i = 0; i < remaining.size(); i++) {
						double minDiversity = Double.POSITIVE_INFINITY;
						for (var s : selected) {
							var content2 = SolutionUtil.toContent(remaining.get(i).solution().files());
							var content1 = SolutionUtil.toContent(s.solution().files());
							double diversity = diversityFunc.applyAsDouble(content2, content1);
							minDiversity = Math.min(minDiversity, diversity);
						}
						if (minDiversity > maxDiversity) {
							maxDiversity = minDiversity;
							bestIdx = i;
						}
					}
					if (bestIdx >= 0) {
						selected.add(remaining.remove(bestIdx));
					}
				}
				selected.stream().map(s -> SolutionUtil.toContent(s.solution().files())).forEach(this.diversitySet::add);
			}
		}
	}

	public void invalidate() {
		cache.clear();
		diversitySet.clear();
	}

	public static double fast(String x, String y) {
		if (x.equals(y)) {
			return 0;
		}
		var lenX = x.length();
		var lenY = y.length();
		var lenDiff = Math.abs(lenX - lenY);
		var linesX = x.split("\n").length;
		var linesY = y.split("\n").length;
		var lineDiff = Math.abs(linesX - linesY);

		var charsX = x.chars().boxed().collect(Collectors.toSet());
		var charsY = y.chars().boxed().collect(Collectors.toSet());
		var charDiff = charsX.size() + charsY.size();
		charsX.retainAll(charsY);
		charDiff -= charsX.size();

		return lenDiff * 0.1 + lineDiff * 10 + charDiff * 0.5;
	}

	public static double levenshtein(String x, String y) {
		return new LevenshteinDistance(null).apply(x, y);
	}
}
