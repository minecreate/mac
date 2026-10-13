package com.linenexus.asset.tenant;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<TenantEntity, String> {
    List<TenantEntity> findAllByOrderByCreatedAtDesc();
}
