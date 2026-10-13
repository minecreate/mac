-- 网关地理位置（设备分布中国地图用）
ALTER TABLE edge_gateway
  ADD COLUMN latitude  DOUBLE NULL COMMENT '纬度' AFTER management_url,
  ADD COLUMN longitude DOUBLE NULL COMMENT '经度' AFTER latitude;
