package openevolve.db;

import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("llm_model")
public record LLMModel(@Id UUID id, String name) {
}
