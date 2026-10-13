-- 工程启动主画面
ALTER TABLE hmi_project
  ADD COLUMN home_screen_id VARCHAR(64) NULL AFTER sort_order;
