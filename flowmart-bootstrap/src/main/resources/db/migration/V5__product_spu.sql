CREATE TABLE `product_spu`
(
    `id`              BIGINT       NOT NULL COMMENT '主键，雪花ID',
    `spu_code`        VARCHAR(64)  NOT NULL COMMENT 'SPU编码，后端生成，全局唯一，永不复用',
    `name`            VARCHAR(128) NOT NULL COMMENT '商品名称',
    `category_id`     BIGINT       NOT NULL COMMENT '叶子类目ID',
    `brand_id`        BIGINT NULL     DEFAULT NULL COMMENT '品牌ID，NULL表示无品牌',
    `main_image_url`  VARCHAR(512) NOT NULL COMMENT '主图URL，必填',
    `carousel_images` JSON         NOT NULL COMMENT '轮播图JSON数组，空集合写 []',
    `spec_json`       JSON         NOT NULL COMMENT '规格JSON数组，空集合写 []',
    `description`     TEXT NULL COMMENT '商品描述',
    `status`          TINYINT      NOT NULL DEFAULT 0 COMMENT '状态 0草稿 1上架 2下架',
    `created_by`      BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人',
    `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_by`      BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人',
    `updated_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                     ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`         BIGINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除，非0=删除时取id',
    `version`         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_spu_code` (`spu_code`),
    KEY               `idx_category_deleted` (`category_id`, `deleted`),
    KEY               `idx_brand_deleted` (`brand_id`, `deleted`),
    KEY               `idx_deleted_status_created` (`deleted`, `status`, `created_at`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT='SPU商品表';