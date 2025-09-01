package openevolve.unit.mapelites;

import org.junit.jupiter.api.Test;
import openevolve.mapelites.DefaultRepository;
import openevolve.mapelites.ParetoComparator;
import openevolve.mapelites.Repository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultRepository Unit Tests")
public class DefaultRepositoryTest {

	private Comparator<Repository.Solution<String>> standardComparator;

	@BeforeEach
	void setUp() {
		standardComparator = (a, b) -> {
			double fa = (Double) a.fitness().get("fitness");
			double fb = (Double) b.fitness().get("fitness");
			return Double.compare(fa, fb); // ascending comparator
		};
	}

	private Repository.Solution<String> makeSolution(double fitness, int islandId) {
		Map<String, Object> fitnessMap = new HashMap<>();
		fitnessMap.put("fitness", fitness);
		return new Repository.Solution<>(UUID.randomUUID(), "sol", null, fitnessMap, 0, islandId,
				new int[] {0});
	}

	private Repository.Solution<String> makeMultiObjectiveSolution(double[] objs, int islandId) {
		Map<String, Object> fitnessMap = new HashMap<>();
		fitnessMap.put("objs", objs);
		return new Repository.Solution<>(UUID.randomUUID(), "sol", null, fitnessMap, 0, islandId,
				new int[] {0});
	}

	@Test
	@DisplayName("Test basic CRUD operations")
	public void testSaveFindDeleteSimple() {
		Repository<String> repo = new DefaultRepository<>(standardComparator, 10, 5, 2);

		var s = makeSolution(1.23, 0);
		repo.save(s);

		assertEquals(1, repo.count());
		assertNotNull(repo.findById(s.id()));
		assertTrue(repo.findAll().contains(s));

		repo.delete(s.id());
		assertNull(repo.findById(s.id()));
		assertEquals(0, repo.count());
	}

	@Test
	@DisplayName("Test island assignment and island-specific queries")
	public void testIslandAssignmentAndFindByIsland() {
		Repository<String> repo = new DefaultRepository<>(standardComparator, 10, 5, 3);

		var s0 = makeSolution(0.1, 0);
		var s1 = makeSolution(0.2, 1);
		var s2 = makeSolution(0.3, 2);
		repo.save(s0);
		repo.save(s1);
		repo.save(s2);

		assertEquals(1, repo.countByIslandId(0));
		assertEquals(1, repo.countByIslandId(1));
		assertEquals(1, repo.countByIslandId(2));

		var island0Solutions = repo.findByIslandId(0);
		assertTrue(island0Solutions.stream().anyMatch(sol -> sol.id().equals(s0.id())));
		assertFalse(island0Solutions.stream().anyMatch(sol -> sol.id().equals(s1.id())));

		// Test querying all islands
		var allIslands = repo.findAllIslands();
		assertEquals(3, allIslands.size());
	}

	@Test
	@DisplayName("Test population trimming keeps best solutions")
	public void testPopulationTrimmingKeepsBest() {
		// comparator ascending; DefaultRepository reverses comparator to treat larger as better
		DefaultRepository<String> repo = new DefaultRepository<>(standardComparator, 2, 1, 2);

		var s1 = makeSolution(1.0, 0);
		var s2 = makeSolution(2.0, 0);
		var s3 = makeSolution(3.0, 0);

		repo.save(s1);
		repo.save(s2);
		repo.save(s3);

		// population should be trimmed to at most populationSize (2)
		assertTrue(repo.count() <= 2);

		// best solution (highest fitness) should still be present
		boolean hasBest = repo.findAll().stream()
				.anyMatch(sol -> ((Double) sol.fitness().get("fitness")).equals(3.0));
		assertTrue(hasBest, "Best solution should be retained after trimming");
	}

	@Test
	@DisplayName("Test archive replacement keeps better solutions")
	public void testArchiveReplacementKeepsBetter() {
		DefaultRepository<String> repo = new DefaultRepository<>(standardComparator, 10, 1, 1);

		var low = makeSolution(1.0, 0);
		var high = makeSolution(5.0, 0);

		repo.save(low);
		// archive size is 1, first saved goes into archive
		assertEquals(1, repo.findAllIslands().get(0).archive().size());

		repo.save(high);

		// archive should contain the higher fitness solution (size may vary depending on
		// implementation)
		var archiveIds = repo.findAllIslands().get(0).archive();
		assertTrue(archiveIds.contains(high.id()));
	}

	@Test
	@DisplayName("Test Pareto comparator integration keeps Pareto-optimal solutions")
	public void testParetoComparatorInRepositoryKeepsParetoOptimal() {
		// two objectives, both maximize
		boolean[] maximize = new boolean[] {true, true};
		ParetoComparator<String> pareto =
				new ParetoComparator<>(maximize, s -> (double[]) s.fitness().get("objs"));

		// small population size to force trimming to the Pareto front
		DefaultRepository<String> repo = new DefaultRepository<>(pareto, 2, 2, 1);

		var s1 = makeMultiObjectiveSolution(new double[] {1.0, 1.0}, 0);
		var s2 = makeMultiObjectiveSolution(new double[] {2.0, 0.0}, 0);
		var s3 = makeMultiObjectiveSolution(new double[] {2.0, 2.0}, 0); // dominates s1 and s2
		var s4 = makeMultiObjectiveSolution(new double[] {1.0, 3.0}, 0); // non-dominated with s3

		// save dominated solutions first, then non-dominated, and save the dominating one last
		repo.save(s1);
		repo.save(s2);
		repo.save(s4);
		repo.save(s3); // save the dominating solution last so it's protected during trimming

		// repository population should be at most 2 and should contain the dominating solution s3
		var all = repo.findAll();
		assertTrue(all.size() <= 2);
		var ids = all.stream().map(Repository.Solution::id)
				.collect(java.util.stream.Collectors.toSet());
		assertTrue(ids.contains(s3.id()),
				"Dominating Pareto-optimal solution s3 should be retained");
	}

	@Test
	@DisplayName("Test repository constructor validation")
	public void testConstructorValidation() {
		assertThrows(NullPointerException.class, () ->
			new DefaultRepository<String>(null, 10, 5, 2),
			"Should throw when comparator is null");

		assertThrows(IllegalArgumentException.class, () ->
			new DefaultRepository<String>(standardComparator, -1, 5, 2),
			"Should throw when populationSize is negative");

		assertThrows(IllegalArgumentException.class, () ->
			new DefaultRepository<String>(standardComparator, 10, -1, 2),
			"Should throw when archiveSize is negative");

		assertThrows(IllegalArgumentException.class, () ->
			new DefaultRepository<String>(standardComparator, 10, 5, 0),
			"Should throw when numIslands is zero");
	}

	@Test
	@DisplayName("Test edge cases with empty repository")
	public void testEmptyRepositoryBehavior() {
		Repository<String> repo = new DefaultRepository<>(standardComparator, 10, 5, 2);

		assertEquals(0, repo.count());
		assertTrue(repo.findAll().isEmpty());
		assertNull(repo.findById(UUID.randomUUID()));
		assertEquals(0, repo.countByIslandId(0));
		assertTrue(repo.findByIslandId(0).isEmpty());

		// Deleting non-existent solution should not throw
		assertDoesNotThrow(() -> repo.delete(UUID.randomUUID()));
	}

	@Test
	@DisplayName("Test repository with different island distributions")
	public void testMultipleIslandBehavior() {
		Repository<String> repo = new DefaultRepository<>(standardComparator, 10, 5, 3);

		// Add solutions to different islands
		var s0a = makeSolution(1.0, 0);
		var s0b = makeSolution(2.0, 0);
		var s1a = makeSolution(3.0, 1);
		var s2a = makeSolution(4.0, 2);

		repo.save(s0a);
		repo.save(s0b);
		repo.save(s1a);
		repo.save(s2a);

		assertEquals(4, repo.count());
		assertEquals(2, repo.countByIslandId(0));
		assertEquals(1, repo.countByIslandId(1));
		assertEquals(1, repo.countByIslandId(2));

		// Test that solutions are correctly assigned to islands
		var island0 = repo.findByIslandId(0);
		assertEquals(2, island0.size());
		assertTrue(island0.stream().anyMatch(s -> s.id().equals(s0a.id())));
		assertTrue(island0.stream().anyMatch(s -> s.id().equals(s0b.id())));
	}

	@Test
	@DisplayName("Test repository snapshot functionality")
	public void testSnapshotFunctionality() {
		Repository<String> repo = new DefaultRepository<>(standardComparator, 10, 5, 2);

		var s1 = makeSolution(1.0, 0);
		var s2 = makeSolution(2.0, 1);
		repo.save(s1);
		repo.save(s2);

		var snapshot = repo.snapshot();
		assertNotNull(snapshot);
		
		// Snapshot should contain repository state
		assertEquals(2, snapshot.solutionsById().size());
		assertEquals(2, snapshot.islands().size());
	}

	@Test
	@DisplayName("Test repository restoration from snapshot")
	public void testRestoreFromSnapshot() {
		Repository<String> repo1 = new DefaultRepository<>(standardComparator, 10, 5, 2);
		Repository<String> repo2 = new DefaultRepository<>(standardComparator, 10, 5, 2);

		var s1 = makeSolution(1.0, 0);
		var s2 = makeSolution(2.0, 1);
		repo1.save(s1);
		repo1.save(s2);

		var snapshot = repo1.snapshot();
		repo2.restore(snapshot);

		assertEquals(repo1.count(), repo2.count());
		assertEquals(repo1.findAll().size(), repo2.findAll().size());
	}
}
