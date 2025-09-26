package openevolve.studio.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.relational.core.mapping.Table;
import com.fasterxml.jackson.annotation.JsonFormat;
import openevolve.domain.FitnessAware;
import static openevolve.StudioConstants.DATE_FORMAT;

@Table("events")
public record Event<T extends Event.Payload>(UUID id, UUID problemId,
		@JsonFormat(pattern = DATE_FORMAT, timezone = "UTC") @CreatedDate Instant dateCreated,
		T payload) {

	public interface Payload {}
			
	public record Run(UUID id) implements Payload {
	}

	public record Progress(UUID runId, String message) implements Payload {
	}

	public record Solution<T extends FitnessAware.Data>(UUID id, UUID parentId, UUID runId, T data,
			Map<String, Object> fitness) implements Payload {

		public openevolve.domain.Solution<T> toCoreSolution() {
			return new openevolve.domain.Solution<>(id, parentId, fitness, data);
		}

		public static <T extends FitnessAware.Data> Solution<T> fromCoreSolution(
				openevolve.domain.Solution<T> sol, UUID runId) {
			return new Solution<>(sol.id(), sol.parentId(), runId, sol.data(), sol.fitness());
		}
	}
}
