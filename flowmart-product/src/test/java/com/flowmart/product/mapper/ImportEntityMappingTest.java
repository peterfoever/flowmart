package com.flowmart.product.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.flowmart.product.entity.*;
import com.flowmart.product.enums.ImportBatchStatus;
import com.flowmart.product.enums.ImportGroupStatus;
import org.apache.ibatis.type.StringTypeHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ImportEntityMappingTest {
    @ParameterizedTest
    @CsvSource({
            "ImportBatchEntity,ImportBatchMapper,import_batch",
            "ImportGroupResultEntity,ImportGroupResultMapper,import_group_result",
            "ImportItemOwnerEntity,ImportItemOwnerMapper,import_item_owner",
            "ImportRowErrorEntity,ImportRowErrorMapper,import_row_error"
    })
    @DisplayName("四张导入表正确映射且不携带不存在的逻辑删除和版本字段")
    void mapsImportTables(String entityName, String mapperName, String tableName) throws Exception {
        Class<?> entity = Class.forName("com.flowmart.product.entity." + entityName);
        Class<?> mapper = Class.forName("com.flowmart.product.mapper." + mapperName);
        var config = new MybatisConfiguration();
        config.addMapper(mapper);
        var table = TableInfoHelper.getTableInfo(entity);
        assertEquals(tableName, table.getTableName());
        assertEquals(IdType.INPUT, table.getIdType());
        assertEquals("id", table.getKeyColumn());
        assertFalse(table.isWithLogicDelete());
        assertFalse(table.isWithVersion());
        String sql = config.getMappedStatement(mapper.getName() + ".selectById").getBoundSql(1L).getSql();
        assertTrue(sql.contains(tableName));
        assertFalse(sql.contains("deleted"));
        assertFalse(sql.contains("version"));
        var columns = table.getFieldList().stream().map(f -> f.getColumn()).collect(Collectors.toSet());
        Set<String> expected = switch (tableName) {
            case "import_batch" -> Set.of("owner_id", "operator_id", "idempotency_key", "request_fingerprint",
                    "file_name", "file_hash", "file_url", "total_spu_count", "success_spu_count", "failed_spu_count",
                    "ungrouped_failed_row_count", "unprocessed_spu_count", "total_row_count", "success_row_count",
                    "failed_row_count", "status", "error_summary", "warnings", "started_at", "finished_at",
                    "created_at", "updated_at", "created_by", "updated_by");
            case "import_group_result" -> Set.of("batch_id", "import_item_key", "import_item_key_normalized",
                    "spu_name", "row_count", "status", "error_code", "error_message", "spu_id", "spu_code",
                    "created_at", "updated_at", "created_by", "updated_by");
            case "import_item_owner" -> Set.of("owner_id", "import_item_key", "import_item_key_normalized",
                    "batch_id", "group_result_id", "spu_id", "spu_code", "created_at", "updated_at", "created_by", "updated_by");
            default -> Set.of("batch_id", "group_result_id", "row_num", "import_item_key", "field",
                    "error_code", "error_message", "created_at");
        };
        assertEquals(expected, columns);
    }

    @Test
    @DisplayName("warnings 使用原始JSON字符串，操作人和归属人分别绑定")
    void batchInsertUsesExplicitActorAndRawJson() {
        var config = new MybatisConfiguration();
        config.addMapper(ImportBatchMapper.class);
        var batch = new ImportBatchEntity();
        batch.setId(1L);
        batch.setOwnerId(100L);
        batch.setOperatorId(200L);
        batch.setCreatedBy(200L);
        batch.setUpdatedBy(200L);
        batch.setWarnings("[]");
        var bound = config.getMappedStatement(ImportBatchMapper.class.getName() + ".insert").getBoundSql(batch);
        var properties = bound.getParameterMappings().stream().map(p -> p.getProperty()).toList();
        assertTrue(properties.containsAll(Set.of("id", "ownerId", "operatorId", "createdBy", "updatedBy", "warnings")));
        var warnings = bound.getParameterMappings().stream()
                .filter(p -> p.getProperty().equals("warnings")).findFirst().orElseThrow();
        assertInstanceOf(StringTypeHandler.class, warnings.getTypeHandler());
    }

    @Test
    @DisplayName("批次和分组状态编码可往返，非法值必须拒绝")
    void statusCodesAreExplicit() {
        for (var status : ImportBatchStatus.values()) assertSame(status, ImportBatchStatus.of(status.getCode()));
        for (var status : ImportGroupStatus.values()) assertSame(status, ImportGroupStatus.of(status.getCode()));
        for (String invalid : new String[]{null, "", "succeeded", "UNKNOWN"}) {
            assertThrows(IllegalArgumentException.class, () -> ImportBatchStatus.of(invalid));
            assertThrows(IllegalArgumentException.class, () -> ImportGroupStatus.of(invalid));
        }
    }
}
