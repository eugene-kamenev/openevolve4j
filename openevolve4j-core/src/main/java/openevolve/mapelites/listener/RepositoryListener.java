package openevolve.mapelites.listener;

import java.nio.file.Path;
import com.fasterxml.jackson.databind.ObjectMapper;
import openevolve.mapelites.Repository.Solution;
import openevolve.util.Util;

public interface RepositoryListener<T> extends Listener {

	default void onSolutionAdded(Solution<T> solution) {};
	default void onSolutionRemoved(Solution<T> solution) {};

	public record SolutionWriter<T>(Path filePath, ObjectMapper mapper) implements RepositoryListener<T> {
		@Override
		public void onSolutionAdded(Solution<T> solution) {
			Util.writeJSONL(solution, mapper, filePath);
		}
	}
}
