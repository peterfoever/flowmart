-- 编码全局唯一且永不复用，与 V6 的有效规格组合唯一约束分别保证两种业务身份。
-- 保留 V6 内容，避免修改可能已经执行的迁移导致 checksum 不一致。
-- 若已有重复编码，本迁移应失败并先由人工核查数据，不能自动删除或重写编码。
ALTER TABLE `product_sku`
    ADD UNIQUE KEY `uk_sku_code` (`sku_code`);
