CREATE TABLE import_row_error
(
    id              BIGINT       NOT NULL,
    batch_id        BIGINT       NOT NULL,
    group_result_id BIGINT NULL,
    row_num         INT          NOT NULL,
    import_item_key VARCHAR(64) NULL,
    field           VARCHAR(64) NULL,
    error_code      VARCHAR(64)  NOT NULL,
    error_message   VARCHAR(512) NOT NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY             idx_import_row_error_batch (batch_id, row_num)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;