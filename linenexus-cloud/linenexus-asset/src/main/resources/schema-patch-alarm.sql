-- 告警规则与活动告警（monitor 服务读写；与 asset 同库）
USE linenexus_asset;

CREATE TABLE IF NOT EXISTS alarm_rule (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL DEFAULT 'tenant-dev',
  name            VARCHAR(128) NOT NULL,
  enabled         TINYINT(1)   NOT NULL DEFAULT 1,
  rule_type       VARCHAR(32)  NOT NULL,
  gateway_id      VARCHAR(64)  NULL,
  device_id       VARCHAR(64)  NULL,
  point_id        VARCHAR(64)  NULL,
  operator        VARCHAR(16)  NULL,
  threshold       DOUBLE       NULL,
  offline_sec     INT          NULL,
  severity        VARCHAR(16)  NOT NULL DEFAULT 'warning',
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_alarm_rule_tenant (tenant_id),
  KEY idx_alarm_rule_type (tenant_id, rule_type, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS alarm_event (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL DEFAULT 'tenant-dev',
  rule_id         VARCHAR(64)  NOT NULL,
  rule_name       VARCHAR(128) NOT NULL,
  rule_type       VARCHAR(32)  NOT NULL,
  severity        VARCHAR(16)  NOT NULL DEFAULT 'warning',
  gateway_id      VARCHAR(64)  NULL,
  device_id       VARCHAR(64)  NULL,
  point_id        VARCHAR(64)  NULL,
  message         VARCHAR(512) NOT NULL,
  value_snapshot  VARCHAR(128) NULL,
  status          VARCHAR(16)  NOT NULL DEFAULT 'active',
  triggered_at    DATETIME(3)  NOT NULL,
  acked_at        DATETIME(3)  NULL,
  acked_by        VARCHAR(128) NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_alarm_event_tenant_status (tenant_id, status, triggered_at),
  KEY idx_alarm_event_rule (rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
