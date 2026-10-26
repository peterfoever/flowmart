package com.flowmart.importing;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flowmart.product.entity.*;
import com.flowmart.product.mapper.*;
import com.flowmart.product.support.ImportItemKeyNormalizer;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.sql.*;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 显式传入专用临时数据库后才执行，绝不读取 dev/test 应用配置。
 * Flyway 保留表结构，每个测试的数据在关闭连接前回滚。运行方法见 docs/testing/FM-007/README.md。
 */
@EnabledIfSystemProperty(named = "import.mysql.url", matches = ".+")
class ImportSchemaMysqlTest {
    private static UnpooledDataSource dataSource;
    private static SqlSessionFactory sessions;
    private Connection connection;

    @BeforeAll
    static void migrateAndConfigure() {
        String url = System.getProperty("import.mysql.url");
        if (!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/fm007_check(?:\\?.*)?")) {
            throw new IllegalArgumentException("只允许连接本机显式端口的 fm007_check 隔离数据库");
        }
        dataSource = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url,
                System.getProperty("import.mysql.user", "root"),
                System.getProperty("import.mysql.password"));
        var flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .cleanDisabled(true).load();
        flyway.migrate();
        flyway.validate();
        assertEquals(0, flyway.migrate().migrationsExecuted, "第二次迁移不能重复执行");

        var config = new MybatisConfiguration();
        config.setEnvironment(new Environment("fm007-isolated", new JdbcTransactionFactory(), dataSource));
        config.addMapper(ImportBatchMapper.class);
        config.addMapper(ImportGroupResultMapper.class);
        config.addMapper(ImportItemOwnerMapper.class);
        config.addMapper(ImportRowErrorMapper.class);
        sessions = new MybatisSqlSessionFactoryBuilder().build(config);
    }

    @BeforeEach
    void begin() throws SQLException {
        connection = dataSource.getConnection();
        connection.setAutoCommit(false);
    }

    @AfterEach
    void rollback() throws SQLException {
        if (connection != null) {
            try { connection.rollback(); } finally { connection.close(); }
        }
    }

    @Test
    @DisplayName("V1到V12全部迁移成功")
    void allMigrationsApplied() throws SQLException {
        assertEquals(12L, scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1"));
    }

    @Test
    @DisplayName("同归属同请求键拒绝，不同归属允许；重复文件哈希只是提示")
    void requestUniquenessAndFileHash() throws SQLException {
        batch(1, 10, "request-1");
        duplicate("uk_import_batch_idem", () -> batch(2, 10, "request-1"));
        batch(3, 20, "request-1");
        batch(4, 10, "request-2");
        assertEquals(3L, scalar("SELECT COUNT(*) FROM import_batch"));
    }

    @Test
    @DisplayName("请求键区分大小写和尾部空格，不使用数据库默认比较语义")
    void requestKeyIsOpaque() throws SQLException {
        batch(1, 10, "Request");
        batch(2, 10, "request");
        batch(3, 10, "request ");
        assertEquals(3L, scalar("SELECT COUNT(*) FROM import_batch"));
    }

    @Test
    @DisplayName("跨文件标识经过规范化后同归属只能占用一次，不同归属互不影响")
    void ownerUniqueness() throws SQLException {
        owner(1, 10, 100, "item-1");
        duplicate("uk_import_owner_key", () -> owner(2, 10, 200, "ITEM-1"));
        owner(3, 20, 200, "item-1");
    }

    @Test
    @DisplayName("来源占位事务回滚后能够重试，不留下永久占位")
    void rollbackReleasesOwnerKey() throws SQLException {
        owner(1, 10, 100, "item-1");
        connection.rollback();
        owner(2, 10, 200, "ITEM-1");
        assertEquals(1L, scalar("SELECT COUNT(*) FROM import_item_owner"));
    }

    @Test
    @DisplayName("组结果按批次和规范化标识唯一，跨批次可重复记录失败或成功")
    void groupUniqueness() throws SQLException {
        group(1, 100);
        duplicate("uk_import_group_batch_key", () -> group(2, 100));
        group(3, 200);
    }

    @Test
    @DisplayName("无法归组的行可保存空关联，同批次行号只允许一条错误")
    void rowErrorsAreUniqueAndNullable() throws SQLException {
        rowError(1, 100, 2);
        duplicate("uk_import_row_error_batch_row", () -> rowError(2, 100, 2));
        rowError(3, 200, 2);
        rowError(4, 100, 3);
        assertEquals(3L, scalar("SELECT COUNT(*) FROM import_row_error WHERE group_result_id IS NULL"));
    }

    @Test
    @DisplayName("warnings实际存储为JSON数组，未赋值计数使用数据库零默认值")
    void jsonAndDefaults() throws SQLException {
        batch(1, 10, "request");
        try (var statement = connection.createStatement();
             var result = statement.executeQuery(
                     "SELECT JSON_TYPE(warnings), total_spu_count, ungrouped_failed_row_count, file_url FROM import_batch")) {
            assertTrue(result.next());
            assertEquals("ARRAY", result.getString(1));
            assertEquals(0, result.getInt(2));
            assertEquals(0, result.getInt(3));
            assertNull(result.getString(4));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "ImportBatchEntity,ImportBatchMapper",
            "ImportGroupResultEntity,ImportGroupResultMapper",
            "ImportItemOwnerEntity,ImportItemOwnerMapper",
            "ImportRowErrorEntity,ImportRowErrorMapper"
    })
    @DisplayName("四张表通过真实Mapper写入和读回，所有字段含审计值一致")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void entityRoundTrip(String entityName, String mapperName) throws Exception {
        Class<?> type = Class.forName("com.flowmart.product.entity." + entityName);
        Object entity = type.getDeclaredConstructor().newInstance();
        // 所有字段均赋非空值，避免仅测试空字段掩盖漏映射；日期精确到毫秒。
        for (var field : type.getDeclaredFields()) {
            field.setAccessible(true);
            Object value;
            if (field.getType() == Long.class) {
                value = field.getName().equals("ownerId") ? 100L : 200L;
            } else if (field.getType() == Integer.class) {
                value = 1;
            } else if (field.getType() == LocalDateTime.class) {
                value = LocalDateTime.of(2026, 10, 22, 9, 0, 0, 123_000_000);
            } else {
                value = field.getName().equals("warnings") ? "[\"重复文件提醒\"]" : "test";
            }
            field.set(entity, value);
        }
        try (var session = sessions.openSession(false)) {
            try {
                var mapper = (BaseMapper) session.getMapper(
                        Class.forName("com.flowmart.product.mapper." + mapperName));
                assertEquals(1, mapper.insert(entity));
                session.clearCache(); // 强制读数据库，不用一级缓存伪造往返成功。
                Object result = mapper.selectById(200L);
                if (result instanceof ImportBatchEntity batch) {
                    assertEquals("ARRAY", jsonType(session.getConnection(), 200));
                    // MySQL JSON 文本可能调整空白，按 JSON 语义比较。
                    var json = new com.fasterxml.jackson.databind.ObjectMapper();
                    assertEquals(json.readTree(((ImportBatchEntity) entity).getWarnings()), json.readTree(batch.getWarnings()));
                    batch.setWarnings(((ImportBatchEntity) entity).getWarnings());
                }
                assertEquals(entity, result);
            } finally {
                session.rollback();
            }
        }
    }

    private String jsonType(Connection conn, long id) throws SQLException {
        try (var stmt = conn.prepareStatement("SELECT JSON_TYPE(warnings) FROM import_batch WHERE id = ?")) {
            stmt.setLong(1, id);
            try (var result = stmt.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private void batch(long id, long owner, String key) throws SQLException {
        execute("INSERT INTO import_batch (id,owner_id,operator_id,idempotency_key,request_fingerprint,"
                        + "file_name,file_hash,status,warnings,created_by,updated_by) VALUES (?,?,99,?,?,?,?,'PENDING','[]',99,99)",
                id, owner, key, "a".repeat(64), "测试.xlsx", "b".repeat(64));
    }

    private void owner(long id, long owner, long batch, String key) throws SQLException {
        execute("INSERT INTO import_item_owner (id,owner_id,import_item_key,import_item_key_normalized,"
                        + "batch_id,created_by,updated_by) VALUES (?,?,?,?,?,99,99)",
                id, owner, key, ImportItemKeyNormalizer.normalize(key), batch);
    }

    private void group(long id, long batch) throws SQLException {
        execute("INSERT INTO import_group_result (id,batch_id,import_item_key,import_item_key_normalized,"
                        + "row_count,status,created_by,updated_by) VALUES (?,?,'item-1','ITEM-1',1,'FAILED',99,99)", id, batch);
    }

    private void rowError(long id, long batch, int row) throws SQLException {
        execute("INSERT INTO import_row_error (id,batch_id,row_num,error_code,error_message) "
                + "VALUES (?,?,?,'PARAM_INVALID','标识缺失')", id, batch, row);
    }

    private void execute(String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private long scalar(String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private void duplicate(String index, org.junit.jupiter.api.function.Executable action) {
        SQLException exception = assertThrows(SQLException.class, action);
        assertEquals(1062, exception.getErrorCode());
        assertTrue(exception.getMessage().contains(index), exception.getMessage());
    }
}
