package rajib.dev.utility.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rajib.dev.utility.entity.SchemaSnapshotEntity;

import java.util.List;

@Repository
public interface SchemaSnapshotRepository extends JpaRepository<SchemaSnapshotEntity, Long> {

    List<SchemaSnapshotEntity> findAllByOrderByCapturedAtDesc();
}
