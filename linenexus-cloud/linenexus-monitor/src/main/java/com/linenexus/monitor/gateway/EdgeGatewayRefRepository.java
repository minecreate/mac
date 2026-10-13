package com.linenexus.monitor.gateway;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EdgeGatewayRefRepository extends JpaRepository<EdgeGatewayRef, String> {

    Optional<EdgeGatewayRef> findById(String id);

    List<EdgeGatewayRef> findByTenantId(String tenantId);
}
