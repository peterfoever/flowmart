CREATE TABLE `product_category_brand`
(
    `id`          BIGINT NOT NULL COMMENT '主键，雪花ID',
    `category_id` BIGINT NOT NULL COMMENT '类目ID',
    `brand_id`    BIGINT NOT NULL COMMENT '品牌ID',
    `created_by`  BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
    `created_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_by`  BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
    `updated_at`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`     BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除，非0=删除时取id',
    `version`     INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category_brand_deleted` (`category_id`, `brand_id`, `deleted`),
    KEY           `idx_brand_id_deleted` (`brand_id`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT='类目品牌关联表';