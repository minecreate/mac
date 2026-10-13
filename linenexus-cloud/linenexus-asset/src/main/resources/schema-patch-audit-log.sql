-- 系统审计日志（工业现场：登录 / 写点 / 告警确认 / 配置变更）
CREATE TABLE IF NOT EXISTS audit_log (
  id           VARCHAR(64)  NOT NULL,
  tenant_id    VARCHAR(64)  NOT NULL DEFAULT 'tenant-dev',
  category     VARCHAR(32)  NOT NULL,
  action       VARCHAR(64)  NOT NULL,
  result       VARCHAR(16)  NOT NULL,
  actor_id     VARCHAR(64)  NULL,
  actor_name   VARCHAR(128) NULL,
  target       VARCHAR(256) NULL,
  summary      VARCHAR(512) NULL,
  detail       MEDIUMTEXT   NULL,
  client_ip    VARCHAR(64)  NULL,
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_audit_tenant_time (tenant_id, created_at),
  KEY idx_audit_category (tenant_id, category),
  KEY idx_audit_actor (tenant_id, actor_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
