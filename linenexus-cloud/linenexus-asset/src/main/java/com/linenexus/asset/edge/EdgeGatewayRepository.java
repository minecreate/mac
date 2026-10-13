package com.linenexus.asset.edge;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EdgeGatewayRepository extends JpaRepository<EdgeGatewayEntity, String> {

    List<EdgeGatewayEntity> findByTenantIdOrderByIdAsc(String tenantId);
}
