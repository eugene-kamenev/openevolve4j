package openevolve.mapelites.listener;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import openevolve.mapelites.MAPElites;
import openevolve.mapelites.Repository;
import openevolve.mapelites.MAPElites.Snapshot;
import openevolve.mapelites.Repository.Island;

public class CheckpointListener<T> implements MAPElitesListener<T> {

	private final Path checkpointDir;
	private final int checkpointInterval;
	private final Repository<T> repository;
	private final ObjectMapper mapper;
	private final Integer checkpoint;

	public CheckpointListener(Path checkpointDir, int checkpointInterval, ObjectMapper mapper, Repository<T> repository, Integer checkpoint) {
		Objects.requireNonNull(checkpointDir, "Checkpoint directory must not be null");
		Objects.requireNonNull(mapper, "ObjectMapper must not be null");
		Objects.requireNonNull(repository, "Repository must not be null");
		if (checkpointInterval <= 0) {
			throw new IllegalArgumentException("Checkpoint interval must be greater than zero");
		}
		this.checkpointDir = checkpointDir;
		this.checkpointInterval = checkpointInterval;
		this.mapper = mapper;
		this.repository = repository;
		this.checkpoint = checkpoint;
	}

	public boolean shouldCheckpoint(int iteration) {
		return iteration > 0 && iteration % checkpointInterval == 0;
	}

	@Override
	public void onAlgorithmStart(MAPElites<T> mapElites) {
		if (checkpoint != null) {
			try {
				loadCheckpoint(checkpointDir.resolve("checkpoint_iter_" + checkpoint + ".json"), mapElites);
			} catch (Exception e) {
				System.err.println("Warning: Failed to load checkpoint at iteration " + checkpoint);
			}
		}
	}

	@Override
	public void onAfterIteration(Island island, int iteration, MAPElites<T> mapElites) {
		if (shouldCheckpoint(iteration)) {
			try {
				Path checkpointPath = checkpointDir.resolve("checkpoint_iter_" + iteration + ".json");
				saveCheckpoint(checkpointPath, iteration, mapElites);
			} catch (Exception e) {
				System.err.println("Warning: Failed to save checkpoint at iteration " + iteration);
				e.printStackTrace();
			}
		}
	}

	private void saveCheckpoint(Path file, int iteration, MAPElites<T> mapElites) throws Exception {
        var cp = mapElites.snapshot(); // + 1 because algorithm will start with this iteration, we save after iteration
        Files.createDirectories(file.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), cp);
    }

	private void loadCheckpoint(Path file, MAPElites<T> mapElites) throws Exception {
        var cp = mapper.readValue(file.toFile(), new TypeReference<Snapshot<T>>() {});
        repository.restore(cp.repository());
        mapElites.setIteration(cp.iteration());
    }
}
