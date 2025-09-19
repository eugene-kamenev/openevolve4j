package openevolve.db;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import com.fasterxml.jackson.annotation.JsonFormat;
import openevolve.StudioConstants;
import openevolve.mapelites.MAPElites.State;

@Table("evolution_state")
public record EvolutionState(@Id UUID runId, @JsonFormat(pattern = StudioConstants.DATE_FORMAT, timezone = "UTC") @CreatedDate Instant dateCreated, State state) {
	
}
