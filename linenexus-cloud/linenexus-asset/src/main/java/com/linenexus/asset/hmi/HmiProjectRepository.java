package com.linenexus.asset.hmi;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HmiProjectRepository extends JpaRepository<HmiProjectEntity, String> {

    List<HmiProjectEntity> findByTenantIdOrderBySortOrderAscUpdatedAtDesc(String tenantId);

    Optional<HmiProjectEntity> findByIdAndTenantId(String id, String tenantId);

    boolean existsByTenantIdAndName(String tenantId, String name);
}
