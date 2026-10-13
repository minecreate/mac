-- P0 登录用户表（可单独执行）
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

-- 已有库补列（可重复执行：列已存在时忽略报错）
-- ALTER TABLE iam_user ADD COLUMN avatar MEDIUMTEXT NULL AFTER display_name;
