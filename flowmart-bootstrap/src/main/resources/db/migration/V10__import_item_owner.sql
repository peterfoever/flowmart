CREATE TABLE import_item_owner
(
    id                         BIGINT      NOT NULL COMMENT '主键，应用生成的雪花ID',
    owner_id                   BIGINT      NOT NULL COMMENT '导入归属主体ID，与规范化商品标识共同组成跨文件唯一键',
    import_item_key            VARCHAR(64) NOT NULL COMMENT '导入商品原始标识，用于展示和追溯',
    import_item_key_normalized VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '规范化商品标识，首尾去空白后以Locale.ROOT转大写',
    batch_id                   BIGINT      NOT NULL COMMENT '首次成功导入的批次ID，事务占位时为当前批次',
    group_result_id            BIGINT NULL COMMENT '成功分组结果ID，事务占位时可为空，提交前必须回填',
    spu_id                     BIGINT NULL COMMENT '成功创建的SPU ID，事务占位时可为空，提交前必须回填',
    spu_code                   VARCHAR(64) NULL COMMENT '成功创建的SPU编码，事务占位时可为空，提交前必须回填',
    created_at                 DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at                 DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                             ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    created_by                 BIGINT      NOT NULL COMMENT '创建操作人ID',
    updated_by                 BIGINT      NOT NULL COMMENT '最后更新操作人ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_import_owner_key (owner_id, import_item_key_normalized)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT='导入商品来源防重映射表';
