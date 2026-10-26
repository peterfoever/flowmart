package com.flowmart.product.support;

import org.junit.jupiter.api.*;
import org.springframework.dao.DuplicateKeyException;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class ImportSqlErrorsTest {
    @Test @DisplayName("仅匹配MySQL1062明确索引名，不匹配主键或相似索引")
    void exactIndex() {
        assertTrue(ImportSqlErrors.duplicateOn(error("import_item_owner.uk_import_owner_key", 1062), "uk_import_owner_key"));
        assertTrue(ImportSqlErrors.duplicateOn(error("uk_import_owner_key", 1062), "uk_import_owner_key"));
        assertFalse(ImportSqlErrors.duplicateOn(error("PRIMARY", 1062), "uk_import_owner_key"));
        assertFalse(ImportSqlErrors.duplicateOn(error("uk_import_owner_key_suffix", 1062), "uk_import_owner_key"));
        assertFalse(ImportSqlErrors.duplicateOn(error("uk_import_owner_key", 1205), "uk_import_owner_key"));
        assertFalse(ImportSqlErrors.duplicateOn(new DuplicateKeyException("uk_import_owner_key"), "uk_import_owner_key"));
    }
    private Exception error(String key, int code) {
        return new DuplicateKeyException("conflict", new SQLException("Duplicate entry 'x' for key '" + key + "'", "23000", code));
    }
}
