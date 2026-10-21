CREATE TABLE import_item_owner
(
    id                         BIGINT      NOT NULL,
    owner_id                   BIGINT      NOT NULL,
    import_item_key            VARCHAR(64) NOT NULL,
    import_item_key_normalized VARCHAR(64) NOT NULL,
    batch_id                   BIGINT      NOT NULL,
    group_result_id            BIGINT NULL,
    spu_id                     BIGINT NULL,
    spu_code                   VARCHAR(64) NULL,
    created_at                 DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at                 DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                             ON UPDATE CURRENT_TIMESTAMP(3),
    created_by                 BIGINT      NOT NULL,
    updated_by                 BIGINT      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_import_owner_key (owner_id, import_item_key_normalized)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;