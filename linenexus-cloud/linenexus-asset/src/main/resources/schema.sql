CREATE DATABASE IF NOT EXISTS linenexus_asset
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE linenexus_asset;

CREATE TABLE IF NOT EXISTS edge_gateway (
  id              VARCHAR(64)  NOT NULL,
  name            VARCHAR(128) NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL DEFAULT 'tenant-dev',
  status          VARCHAR(32)  NOT NULL DEFAULT 'online',
  last_seen_at    DATETIME(3)  NULL,
  management_url  VARCHAR(512) NULL,
  latitude        DOUBLE       NULL,
  longitude       DOUBLE       NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_edge_gateway_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- HMI 工程（一工程一入口；home_screen_id 为启动主画面）
CREATE TABLE IF NOT EXISTS hmi_project (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL DEFAULT 'tenant-dev',
  name            VARCHAR(128) NOT NULL,
  sort_order      INT          NOT NULL DEFAULT 0,
  home_screen_id  VARCHAR(64)  NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_hmi_project_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- HMI 画面（短期挂在 asset 库；接口路径 /hmi/*，便于迁到 monitor）
CREATE TABLE IF NOT EXISTS hmi_screen (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL DEFAULT 'tenant-dev',
  project_id      VARCHAR(64)  NULL,
  name            VARCHAR(128) NOT NULL,
  version         INT          NOT NULL DEFAULT 1,
  status          VARCHAR(32)  NOT NULL DEFAULT 'draft',
  document_json   LONGTEXT     NOT NULL,
  updated_by      VARCHAR(128) NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_hmi_screen_tenant (tenant_id),
  KEY idx_hmi_screen_status (tenant_id, status),
  KEY idx_hmi_screen_project (tenant_id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 租户主数据（平台运营面管理；业务表仍用 tenant_id 行级隔离）
CREATE TABLE IF NOT EXISTS tenant (
  id              VARCHAR(64)  NOT NULL,
  name            VARCHAR(128) NOT NULL,
  status          VARCHAR(32)  NOT NULL DEFAULT 'active',
  remark          VARCHAR(512) NULL,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_tenant_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 登录用户（P0 挂在 asset；后续可迁 iam 服务）
-- 平台超管：tenant_id = __platform__，roles 含 platform_admin
CREATE TABLE IF NOT EXISTS iam_user (
  id              VARCHAR(64)  NOT NULL,
  tenant_id       VARCHAR(64)  NOT NULL DEFAULT 'tenant-dev',
  username        VARCHAR(64)  NOT NULL,
  display_name    VARCHAR(128) NULL,
  avatar          MEDIUMTEXT   NULL,
  password_hash   VARCHAR(100) NOT NULL,
  roles           VARCHAR(128) NOT NULL,
  enabled         TINYINT(1)   NOT NULL DEFAULT 1,
  created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_iam_user_tenant_name (tenant_id, username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 告警规则（monitor）
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

-- 告警事件（monitor）
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

-- 系统审计日志（登录 / 写点 / 告警确认 / 配置变更）
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
