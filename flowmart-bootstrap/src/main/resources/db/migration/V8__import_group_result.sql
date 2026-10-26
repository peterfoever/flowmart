CREATE TABLE import_group_result
(
    id                         BIGINT      NOT NULL COMMENT '主键，应用生成的雪花ID',
    batch_id                   BIGINT      NOT NULL COMMENT '导入批次ID，关联import_batch.id',
    import_item_key            VARCHAR(64) NOT NULL COMMENT '导入商品原始标识，用于展示和追溯',
    import_item_key_normalized VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '规范化商品标识，首尾去空白后以Locale.ROOT转大写',
    spu_name                   VARCHAR(128) NULL COMMENT '商品名称，校验失败未取得名称时可为空',
    row_count                  INT         NOT NULL COMMENT '该商品组包含的数据行数',
    status                     VARCHAR(16) NOT NULL COMMENT '分组结果：SUCCEEDED成功，FAILED失败',
    error_code                 VARCHAR(64) NULL COMMENT '分组错误码，成功时为空',
    error_message              VARCHAR(512) NULL COMMENT '分组错误说明，成功时为空',
    spu_id                     BIGINT NULL COMMENT '成功创建的SPU ID，失败时为空',
    spu_code                   VARCHAR(64) NULL COMMENT '成功创建的SPU编码，失败时为空',
    created_at                 DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at                 DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                             ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    created_by                 BIGINT      NOT NULL COMMENT '创建操作人ID',
    updated_by                 BIGINT      NOT NULL COMMENT '最后更新操作人ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_import_group_batch_key (batch_id, import_item_key_normalized),
    KEY                        idx_import_group_batch (batch_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT='导入商品分组结果表';
