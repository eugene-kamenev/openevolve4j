package openevolve.mapelites;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public interface Population<T> {

	void save(Solution<T> solution);

	void delete(UUID id);

	Solution<T> best();

	Solution<T> findById(UUID id);

	List<Solution<T>> findAll();

	List<Solution<T>> findByIslandId(int islandId);

	List<Solution<T>> getArchive();

	int count();

	int countByIslandId(int islandId);

	int compare(Solution<T> a, Solution<T> b);

	boolean dominates(Solution<T> a, Solution<T> b);

	Island nextIsland();

	Island findIslandById(int islandId);

	List<Island> findAllIslands();

	// Snapshot/restore support
	PopulationState snapshot();

	void restore(PopulationState state, Map<UUID, Solution<T>> allSolutions);

	// Portable DTOs for checkpointing
	public record PopulationState(List<UUID> solutions, Set<UUID> archive, List<List<UUID>> islands, Integer currentIslandId) {
	}

	public record MAPElitesMetadata(
			UUID migratedFrom,
			int iteration,
			int islandId,
			int[] cell,
			String cellId) {
		public MAPElitesMetadata(UUID migratedFrom, int iteration, int islandId, int[] cell) {
			this(migratedFrom, iteration, islandId, cell, Solution.cellToKey(cell));
		}

		public MAPElitesMetadata {
			Objects.requireNonNull(cell, "cell must not be null");
			if (cell.length == 0) {
				throw new IllegalArgumentException("cell must not be empty");
			}
			if (cellId == null || cellId.isEmpty()) {
				cellId = Solution.cellToKey(cell);
			}
		}
	}

	public record Solution<T>(
			UUID id, UUID parentId, UUID runId, Instant dateCreated, T solution,
			Map<String, Object> fitness, MAPElitesMetadata metadata) {

		public Solution {
			Objects.requireNonNull(id, "id must not be null");
			if (fitness == null || fitness.isEmpty()) {
				throw new IllegalArgumentException("fitness must not be null or empty");
			}
		}

		public static String cellToKey(int[] cell) {
			return String.join("-", Arrays.stream(cell).mapToObj(String::valueOf).toArray(String[]::new));
		}

		public Solution<T> copy(int targetIslandId) {
			return new Solution<T>(UUID.randomUUID(), parentId(), runId(), Instant.now(), solution(), fitness(),
					new MAPElitesMetadata(id(), metadata().iteration(), targetIslandId, metadata().cell()));
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((id == null) ? 0 : id.hashCode());
			return result;
		}

		@Override
		@SuppressWarnings("unchecked")
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Solution<T> other = (Solution<T>) obj;
			if (id == null) {
				if (other.id != null)
					return false;
			} else if (!id.equals(other.id))
				return false;
			return true;
		}
	}

	public record Island(int id, Set<UUID> archive) {
		
		public Island(int id) {
			this(id, new TreeSet<>(Comparator.naturalOrder()));
		}

		public int size() {
			return archive.size();
		}

		@Override
		public String toString() {
			return "Island [id=" + id + ", archive=" + archive.size() + "]";
		}
	}
}
