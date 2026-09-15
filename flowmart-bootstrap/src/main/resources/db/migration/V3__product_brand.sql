CREATE TABLE `product_brand`
(
    `id`         BIGINT       NOT NULL COMMENT '主键，雪花ID',
    `name`       VARCHAR(64)  NOT NULL COMMENT '品牌名称',
    `logo_url`   VARCHAR(256) NOT NULL DEFAULT '' COMMENT '品牌Logo',
    `initial`    CHAR(1)      NOT NULL COMMENT '品牌首字母，A-Z',
    `sort_no`    INT          NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    `status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1启用 0禁用',
    `created_by` BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人',
    `created_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_by` BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人',
    `updated_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`    BIGINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除，非0=删除时取id',
    `version`    INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name_deleted` (`name`, `deleted`),
    KEY          `idx_initial_status_sort` (`initial`, `status`, `sort_no`),
    KEY          `idx_status_sort` (`status`, `sort_no`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT='商品品牌表';