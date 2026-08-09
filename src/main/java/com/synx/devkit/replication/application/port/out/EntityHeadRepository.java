package com.synx.devkit.replication.application.port.out;

import com.synx.devkit.replication.domain.model.EntityHead;
import java.util.Optional;
import java.util.UUID;

public interface EntityHeadRepository {
    Optional<EntityHead> find(UUID accountId, String recordId);

    void save(EntityHead head);
}
