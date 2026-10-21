CREATE TABLE import_group_result
(
    id                         BIGINT      NOT NULL,
    batch_id                   BIGINT      NOT NULL,
    import_item_key            VARCHAR(64) NOT NULL,
    import_item_key_normalized VARCHAR(64) NOT NULL,
    spu_name                   VARCHAR(128) NULL,
    row_count                  INT         NOT NULL,
    status                     VARCHAR(16) NOT NULL,
    error_code                 VARCHAR(64) NULL,
    error_message              VARCHAR(512) NULL,
    spu_id                     BIGINT NULL,
    spu_code                   VARCHAR(64) NULL,
    created_at                 DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at                 DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                             ON UPDATE CURRENT_TIMESTAMP(3),
    created_by                 BIGINT      NOT NULL,
    updated_by                 BIGINT      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_import_group_batch_key (batch_id, import_item_key_normalized),
    KEY                        idx_import_group_batch (batch_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;