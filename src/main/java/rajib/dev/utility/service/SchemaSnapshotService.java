package rajib.dev.utility.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;
import rajib.dev.utility.dto.SchemaSnapshotDetailDto;
import rajib.dev.utility.dto.SchemaSnapshotSummaryDto;
import rajib.dev.utility.entity.SchemaSnapshotEntity;
import rajib.dev.utility.model.SchemaSnapshot;
import rajib.dev.utility.repository.SchemaSnapshotRepository;

import java.time.Instant;
import java.util.List;

@Service
public class SchemaSnapshotService {

    private final SchemaSnapshotRepository repository;
    private final ObjectMapper objectMapper;

    public SchemaSnapshotService(SchemaSnapshotRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public SchemaSnapshotEntity save(SchemaSnapshot snapshot) {
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            SchemaSnapshotEntity entity = new SchemaSnapshotEntity(
                    snapshot.name(),
                    snapshot.schemaName(),
                    snapshot.capturedAt() != null ? snapshot.capturedAt() : Instant.now(),
                    snapshot.tables().size(),
                    json
            );
            return repository.save(entity);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize schema snapshot", e);
        }
    }

    public List<SchemaSnapshotSummaryDto> listAll() {
        return repository.findAllByOrderByCapturedAtDesc().stream()
                .map(e -> new SchemaSnapshotSummaryDto(
                        e.getId(), e.getName(), e.getSchemaName(),
                        e.getCapturedAt(), e.getTableCount()))
                .toList();
    }

    public SchemaSnapshotDetailDto getDetail(Long id) {
        SchemaSnapshotEntity entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Snapshot not found: " + id));
        SchemaSnapshot snapshot = deserialize(entity.getSchemaJson());
        return new SchemaSnapshotDetailDto(
                entity.getId(), entity.getName(), entity.getSchemaName(),
                entity.getCapturedAt(), snapshot.tables());
    }

    public SchemaSnapshot getSnapshot(Long id) {
        SchemaSnapshotEntity entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Snapshot not found: " + id));
        return deserialize(entity.getSchemaJson());
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    private SchemaSnapshot deserialize(String json) {
        try {
            return objectMapper.readValue(json, SchemaSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize schema snapshot", e);
        }
    }
}
