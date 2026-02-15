package rajib.dev.utility.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "schema_snapshots")
public class SchemaSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String schemaName;

    @Column(nullable = false)
    private Instant capturedAt;

    @Column(nullable = false)
    private int tableCount;

    @Lob
    @Column(nullable = false, columnDefinition = "CLOB")
    private String schemaJson;

    public SchemaSnapshotEntity() {}

    public SchemaSnapshotEntity(String name, String schemaName, Instant capturedAt, int tableCount, String schemaJson) {
        this.name = name;
        this.schemaName = schemaName;
        this.capturedAt = capturedAt;
        this.tableCount = tableCount;
        this.schemaJson = schemaJson;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }

    public Instant getCapturedAt() { return capturedAt; }
    public void setCapturedAt(Instant capturedAt) { this.capturedAt = capturedAt; }

    public int getTableCount() { return tableCount; }
    public void setTableCount(int tableCount) { this.tableCount = tableCount; }

    public String getSchemaJson() { return schemaJson; }
    public void setSchemaJson(String schemaJson) { this.schemaJson = schemaJson; }
}
