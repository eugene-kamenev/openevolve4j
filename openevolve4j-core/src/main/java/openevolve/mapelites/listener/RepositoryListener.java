package openevolve.mapelites.listener;

import openevolve.mapelites.Population.Solution;

public interface RepositoryListener<T> extends Listener {
	default void onSolutionAdded(Solution<T> solution) {};
	default void onSolutionRemoved(Solution<T> solution) {};
}
