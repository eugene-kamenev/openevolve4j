package openevolve;

import java.nio.file.Files;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.ObjectMapper;
import openevolve.mapelites.MAPElites;
import openevolve.mapelites.Repository;
import openevolve.mapelites.Repository.Island;
import openevolve.mapelites.listener.CheckpointListener;

public class OpenEvolveCheckpointListener extends CheckpointListener<EvolveSolution> {
	private final Path checkpointDir;
	private final Repository<EvolveSolution> repository;

	public OpenEvolveCheckpointListener(Path checkpointDir, int checkpointInterval, ObjectMapper mapper, Repository<EvolveSolution> repository, Integer checkpoint) {
		super(checkpointDir, checkpointInterval, mapper, repository, checkpoint);
		this.checkpointDir = checkpointDir;
		this.repository = repository;
	}

	@Override
	public void onAfterIteration(Island island, int iteration,
			MAPElites<EvolveSolution> mapElites) {
		super.onAfterIteration(island, iteration, mapElites);
		if (shouldCheckpoint(iteration)) {
			var checkpoint = checkpointDir.resolve(String.valueOf(iteration));
			try {
				Files.createDirectories(checkpoint);
				var solutions = repository.findAll();
				for (var solution : solutions) {
					var solutionPath = checkpoint.resolve(String.valueOf(solution.id()));
					Files.walkFileTree(solution.solution().path(), Constants.COPY_VISITOR.apply(solution.solution().path(), solutionPath));
				}
			} catch (Throwable t) {
				System.err.println("Warning: Failed to save checkpoint at iteration " + iteration);
				t.printStackTrace();
			}
		}
	}
}
