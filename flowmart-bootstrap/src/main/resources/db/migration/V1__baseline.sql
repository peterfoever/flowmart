-- =============================================================================
-- V1 基线脚本
--
-- Flyway 铁律（真实工作中违反任何一条都可能酿成线上事故）：
--   1. 已经执行过的脚本文件【绝对不能再改】，改了校验和就对不上，应用启动直接失败。
--      改错了怎么办？加一个新版本的脚本去修正它。
--   2. 版本号只增不减，多人协作时用 V{日期}{序号} 规避冲突，例如 V20260803_01。
--   3. 生产环境的 DDL 必须可回滚 —— 每个 V 脚本都要在 PR 描述里写清楚回滚方案。
--   4. 大表加字段/加索引必须评估锁表时间，MySQL 8 的 ALGORITHM=INSTANT 能加的字段优先用。
--
-- 全表通用列约定（所有业务表都必须有，对应 BaseEntity）：
--   created_by / created_at / updated_by / updated_at —— 审计
--   deleted  BIGINT  —— 逻辑删除，0=未删除，非0=删除时间戳
--   version  INT     —— 乐观锁
--
-- 为什么 deleted 用时间戳而不是 0/1：
--   业务上「同一个编码不能重复」需要唯一索引 uk(code, deleted)。如果 deleted 只有 0/1，
--   同一个 code 删两次就会撞唯一键。用删除时间戳则每次删除的值都不同，天然规避。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_user`
(
    `id`         BIGINT       NOT NULL COMMENT '主键，雪花ID',
    `username`   VARCHAR(64)  NOT NULL COMMENT '登录名',
    `nickname`   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '显示名',
    `password`   VARCHAR(128) NOT NULL DEFAULT '' COMMENT '密码哈希，BCrypt',
    `phone`      VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '手机号',
    `status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1启用 0禁用',
    `created_by` BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人',
    `created_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_by` BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人',
    `updated_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted`    BIGINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除',
    `version`    INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username_deleted` (`username`, `deleted`),
    KEY `idx_created_at` (`created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='系统用户';

-- id=0 是系统账号，定时任务、数据初始化等无登录上下文的场景挂在它名下
INSERT INTO `sys_user` (`id`, `username`, `nickname`, `status`)
SELECT 0, 'system', '系统', 1
WHERE NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `id` = 0);
