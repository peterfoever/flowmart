CREATE TABLE import_row_error
(
    id              BIGINT       NOT NULL COMMENT '主键，应用生成的雪花ID',
    batch_id        BIGINT       NOT NULL COMMENT '导入批次ID，关联import_batch.id',
    group_result_id BIGINT NULL COMMENT '所属分组结果ID，无法归组的行为空',
    row_num         INT          NOT NULL COMMENT 'Excel原始行号，从1开始计数，同批次每行仅一条错误',
    import_item_key VARCHAR(64) NULL COMMENT '原始导入商品标识，缺失时为空，超长非法值仅截断用于展示',
    field           VARCHAR(64) NULL COMMENT '错误字段名，同组连带失败使用GROUP，无法定位时可为空',
    error_code      VARCHAR(64)  NOT NULL COMMENT '行错误码，连带失败为GROUP_FAILED',
    error_message   VARCHAR(512) NOT NULL COMMENT '行错误说明，供用户定位和修正数据',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    -- 每个失败行只记一条代表错误，避免重试写入造成失败行数翻倍。
    UNIQUE KEY uk_import_row_error_batch_row (batch_id, row_num)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT='导入行级错误表';
