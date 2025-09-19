package openevolve.db;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import com.fasterxml.jackson.annotation.JsonFormat;
import openevolve.OpenEvolveConfig;
import openevolve.StudioConstants;

@Table("evolution_run")
public record EvolutionRun(
	@Id UUID id,
	UUID problemId,
	@JsonFormat(pattern = StudioConstants.DATE_FORMAT, timezone = "UTC")
	@CreatedDate
	Instant dateCreated,
	OpenEvolveConfig config
) { }
