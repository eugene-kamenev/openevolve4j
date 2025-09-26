package openevolve.studio.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import com.fasterxml.jackson.annotation.JsonFormat;
import openevolve.domain.PuctTreeConfig;
import static openevolve.StudioConstants.DATE_FORMAT;

@Table("problem")
public record Problem(@Id UUID id, String name, @JsonFormat(pattern = DATE_FORMAT, timezone = "UTC") @CreatedDate Instant dateCreated, PuctTreeConfig config) {
	public Problem {
		if (id == null) {
			id = UUID.randomUUID();
		}
	}
}
