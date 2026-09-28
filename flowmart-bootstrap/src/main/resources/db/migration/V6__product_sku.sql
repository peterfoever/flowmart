CREATE TABLE `product_sku`
(
    `id`          BIGINT         NOT NULL COMMENT '主键，雪花ID',
    `sku_code`    VARCHAR(64)    NOT NULL COMMENT 'SKU编码，后端生成，全局唯一，永不复用',
    `spu_id`      BIGINT         NOT NULL COMMENT '绑定的SPU的id',
    `price`       DECIMAL(10, 2) NOT NULL COMMENT '统一初始价格、允许零元、最多两位小数',
    `spec_values` JSON           NOT NULL COMMENT '规格组合，规范化后的 JSON',
    `spec_hash`   CHAR(64)       NOT NULL COMMENT '规格组合 SHA-256，用于唯一性',
    `is_default`  BOOLEAN        NOT NULL DEFAULT FALSE COMMENT '无规格商品的默认 SKU',
    `image_url`   VARCHAR(512)   NOT NULL COMMENT '图片URL，必填',
    `created_by`  BIGINT         NOT NULL DEFAULT 0 COMMENT '创建人',
    `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_by`  BIGINT         NOT NULL DEFAULT 0 COMMENT '更新人',
    `updated_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                     ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`     BIGINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除，非0=删除时取id',
    `version`     INT            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY uk_spu_spec_deleted (spu_id, spec_hash, deleted),
    KEY idx_spu_deleted_id (spu_id, deleted, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT='SKU商品表';