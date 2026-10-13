-- 平台租户主数据 + 运营面（可单独执行）
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

INSERT INTO tenant (id, name, status, remark)
SELECT 'tenant-dev', '开发租户', 'active', '本地开发默认租户'
WHERE NOT EXISTS (SELECT 1 FROM tenant WHERE id = 'tenant-dev');
