CREATE TABLE IF NOT EXISTS `product_category`
(
    `id`         BIGINT       NOT NULL COMMENT '主键，雪花ID',
    `parent_id`  BIGINT       NOT NULL COMMENT '父类目 ID，一级类目为 0',
    `name`       VARCHAR(64)  NOT NULL COMMENT '类目名称',
    `icon_url`   VARCHAR(256) NOT NULL DEFAULT '' COMMENT '类目图标',
    `level`      INT          NOT NULL COMMENT '层级，1 起',
    `sort_no`    INT          NOT NULL DEFAULT 0 COMMENT '同级排序，越小越靠前，默认 0',
    `status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1启用 0禁用',
    `created_by` BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人',
    `created_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_by` BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人',
    `updated_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`    BIGINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除',
    `version`    INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_parent_name_deleted` (`parent_id`,`name`, `deleted`)
    ) ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_general_ci COMMENT ='商品类目';