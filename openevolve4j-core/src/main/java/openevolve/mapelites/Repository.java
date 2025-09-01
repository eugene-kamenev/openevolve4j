package openevolve.mapelites;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public interface Repository<T> {
	
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
    RepositoryState<T> snapshot();
    void restore(RepositoryState<T> state);

    // Portable DTOs for checkpointing
    public record RepositoryState<T>(
        Map<UUID, Solution<T>> solutionsById,
        Set<UUID> archive,
        List<IslandState> islands,
        Integer currentIslandId
    ) {}

    public record IslandState(int id, Set<UUID> archive) {}

	public record Solution<T>(UUID id, T solution, UUID migratedFrom, Map<String, Object> fitness,
			int iteration, int islandId, int[] cell, String cellId) {

		public Solution(UUID id, T solution, UUID migratedFrom, Map<String, Object> fitness,
				int iteration, int islandId, int[] cell) {
			this(id, solution, migratedFrom, fitness, iteration, islandId, cell, cellToKey(cell));
		}

		public Solution {
			Objects.requireNonNull(id, "id must not be null");
			Objects.requireNonNull(solution, "solution must not be null");
			if (fitness == null || fitness.isEmpty()) {
				throw new IllegalArgumentException("fitness must not be null or empty");
			}
			if (cell == null || cell.length == 0) {
				throw new IllegalArgumentException("cell must not be null or empty");
			}
			if (cellId == null || cellId.trim().isEmpty()) {
				throw new IllegalArgumentException("cellId must not be null or empty");
			}
		}

		public static String cellToKey(int[] cell) {
			return String.join("-",
					Arrays.stream(cell).mapToObj(String::valueOf).toArray(String[]::new));
		}

		public Solution<T> copy(int targetIslandId) {
			return new Solution<T>(UUID.randomUUID(), solution(), id(), fitness(), iteration(),
					targetIslandId, cell());
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

	public static class Island {
		private final int id;
		private final Set<UUID> archive = new HashSet<>();

		public Island(int id) {
			this.id = id;
		}

		public int id() {
			return id;
		}

		public Set<UUID> archive() {
			return archive;
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
