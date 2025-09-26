package openevolve.studio.domain;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import com.fasterxml.jackson.annotation.JsonFormat;
import static openevolve.StudioConstants.DATE_FORMAT;

@Table("llm_model")
public record LLMModel(@Id UUID id, @JsonFormat(pattern = DATE_FORMAT, timezone = "UTC") @CreatedDate Instant dateCreated, String name) {
}
