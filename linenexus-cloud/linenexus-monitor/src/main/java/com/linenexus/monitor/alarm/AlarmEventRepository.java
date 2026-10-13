package com.linenexus.monitor.alarm;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlarmEventRepository extends JpaRepository<AlarmEventEntity, String> {

    List<AlarmEventEntity> findByTenantIdAndStatusOrderByTriggeredAtDesc(String tenantId, String status);

    List<AlarmEventEntity> findByTenantIdOrderByTriggeredAtDesc(String tenantId);

    Optional<AlarmEventEntity> findFirstByTenantIdAndRuleIdAndStatus(String tenantId, String ruleId, String status);

    Optional<AlarmEventEntity> findByIdAndTenantId(String id, String tenantId);
}
