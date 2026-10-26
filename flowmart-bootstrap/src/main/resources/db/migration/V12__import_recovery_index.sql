-- 不改已执行迁移；超时扫描按状态和最近推进时间定位，避免扫描全部导入历史。
CREATE INDEX idx_import_batch_status_updated ON import_batch (status, updated_at);
