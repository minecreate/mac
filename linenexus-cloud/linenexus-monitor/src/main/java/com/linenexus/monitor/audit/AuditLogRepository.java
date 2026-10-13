package com.linenexus.monitor.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, String> {}
