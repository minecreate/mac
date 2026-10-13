package com.linenexus.asset.hmi;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HmiScreenRepository extends JpaRepository<HmiScreenEntity, String> {

    List<HmiScreenEntity> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    List<HmiScreenEntity> findByTenantIdAndStatusOrderByUpdatedAtDesc(String tenantId, String status);

    List<HmiScreenEntity> findByTenantIdAndProjectIdOrderByUpdatedAtDesc(String tenantId, String projectId);

    List<HmiScreenEntity> findByTenantIdAndProjectIdAndStatusOrderByUpdatedAtDesc(
            String tenantId, String projectId, String status);
}
