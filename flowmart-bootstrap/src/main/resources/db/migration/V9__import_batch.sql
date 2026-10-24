CREATE TABLE import_batch
(
    id                         BIGINT       NOT NULL COMMENT '主键，应用生成的雪花ID',
    owner_id                   BIGINT       NOT NULL COMMENT '导入归属主体ID，请求幂等和跨文件防重的隔离范围',
    operator_id                BIGINT       NOT NULL COMMENT '本次导入操作人ID，与归属主体可不同',
    -- 请求键是大小写敏感的不透明标识；NO PAD 避免尾部空格被数据库隐式忽略。
    idempotency_key            VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL COMMENT '客户端请求幂等键，同一归属内唯一，区分大小写和尾部空格',
    request_fingerprint        CHAR(64)     NOT NULL COMMENT '请求指纹，文件哈希、模板版本及规范化选项的SHA-256',
    file_name                  VARCHAR(255) NOT NULL COMMENT '上传文件原始名称',
    file_hash                  CHAR(64)     NOT NULL COMMENT '文件内容的SHA-256，同文件仅提示不禁止重复上传',
    file_url                   VARCHAR(512) NULL COMMENT '上传文件存储地址，文件未落盘时可为空',
    total_spu_count            INT          NOT NULL DEFAULT 0 COMMENT '可确定分组的商品总数，不含无法归组的行',
    success_spu_count          INT          NOT NULL DEFAULT 0 COMMENT '成功导入的商品组数',
    failed_spu_count           INT          NOT NULL DEFAULT 0 COMMENT '已确认失败并记录结果的商品组数',
    ungrouped_failed_row_count INT          NOT NULL DEFAULT 0 COMMENT '无法归组的失败行数，已包含在failed_row_count中',
    unprocessed_spu_count      INT          NOT NULL DEFAULT 0 COMMENT '未得到成功或失败结果的商品组数',
    total_row_count            INT          NOT NULL DEFAULT 0 COMMENT '文件数据行总数，不含表头',
    success_row_count          INT          NOT NULL DEFAULT 0 COMMENT '成功商品组的数据行数之和',
    failed_row_count           INT          NOT NULL DEFAULT 0 COMMENT '失败商品组行数加无法归组的失败行数，不含未处理行',
    status                     VARCHAR(16)  NOT NULL COMMENT '批次状态：PENDING待处理，RUNNING处理中，SUCCEEDED成功，PARTIAL部分成功，FAILED失败，INTERRUPTED中断',
    error_summary              VARCHAR(512) NULL COMMENT '批次级错误或中断原因摘要，无错误时为空',
    warnings                   JSON NULL COMMENT '非阻断警告JSON数组，应用创建批次时显式写入空数组',
    started_at                 DATETIME(3)  NULL COMMENT '批次开始处理时间，未开始时为空',
    finished_at                DATETIME(3)  NULL COMMENT '批次结束或中断时间，未结束时为空',
    created_at                 DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at                 DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                        ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后更新时间',
    created_by                 BIGINT       NOT NULL COMMENT '创建操作人ID',
    updated_by                 BIGINT       NOT NULL COMMENT '最后更新操作人ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_import_batch_idem (owner_id, idempotency_key),
    KEY                        idx_import_batch_hash (owner_id, file_hash),
    KEY                        idx_import_batch_status (status, started_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT='商品导入批次表';
