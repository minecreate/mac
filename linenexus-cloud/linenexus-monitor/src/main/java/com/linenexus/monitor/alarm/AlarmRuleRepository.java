package com.linenexus.monitor.alarm;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlarmRuleRepository extends JpaRepository<AlarmRuleEntity, String> {

    List<AlarmRuleEntity> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    List<AlarmRuleEntity> findByTenantIdAndEnabledTrueAndRuleType(String tenantId, String ruleType);

    List<AlarmRuleEntity> findByEnabledTrueAndRuleType(String ruleType);
}
