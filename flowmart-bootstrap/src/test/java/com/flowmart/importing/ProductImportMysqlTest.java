package com.flowmart.importing;

import com.flowmart.FlowmartApplication;
import com.flowmart.common.exception.BizException;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.service.importing.*;
import com.flowmart.product.support.ImportExcelReader;
import com.flowmart.product.vo.imports.*;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 显式隔离MySQL + 完整Spring代理 + 真实Mapper；不使用H2模拟事务行为。 */
@EnabledIfSystemProperty(named = "import.mysql.url", matches = ".+")
@SpringBootTest(classes = FlowmartApplication.class, properties = {"flowmart.imports.recovery-enabled=false",
        "logging.config=classpath:logback-import-test.xml",
        "spring.datasource.hikari.minimum-idle=1", "spring.datasource.hikari.maximum-pool-size=8"})
@AutoConfigureMockMvc
@ActiveProfiles("import-it")
class ProductImportMysqlTest {
    private static final Path STORAGE = Path.of(System.getProperty("java.io.tmpdir"), "fm007-it-" + UUID.randomUUID());
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getProperty("import.mysql.url");
        if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/fm007_check(?:\\?.*)?")) {
            throw new IllegalArgumentException("仅允许显式本地端口fm007_check隔离库");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getProperty("import.mysql.user", "root"));
        registry.add("spring.datasource.password", () -> System.getProperty("import.mysql.password"));
        registry.add("flowmart.imports.storage-dir", STORAGE::toString);
    }
    @Autowired ProductImportService service;
    @Autowired ImportGroupWriter writer;
    @Autowired ImportBatchTransactions transactions;
    @Autowired ImportQueryService queries;
    @Autowired ImportExcelReader reader;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach void fixture() {
        clear();
        jdbc.update("INSERT INTO product_category(id,parent_id,name,level,status) VALUES (900001,0,'导入测试类目',1,1)");
        jdbc.update("INSERT INTO product_brand(id,name,initial,status) VALUES (900002,'导入测试品牌','D',1)");
        jdbc.update("INSERT INTO product_category_brand(id,category_id,brand_id) VALUES (900003,900001,900002)");
    }
    @AfterEach void clear() {
        // DynamicPropertySource先验证专用库；这些DELETE只清理本测试库，不读取dev/test配置。
        jdbc.execute("DROP TRIGGER IF EXISTS fm007_fail_sku");
        jdbc.execute("DROP TRIGGER IF EXISTS fm007_fail_report");
        for (String table : List.of("import_row_error", "import_group_result", "import_item_owner", "import_batch",
                "product_sku", "product_spu", "product_category_brand", "product_brand", "product_category")) {
            jdbc.execute("DELETE FROM " + table);
        }
    }
    @AfterAll static void files() throws IOException {
        if (Files.exists(STORAGE)) {
            try (var paths = Files.walk(STORAGE)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
    private String[] row(String key) { return new String[]{key, "导入商品", "900001", "", "main.png", "", "", "12.30", "sku.png"}; }
    private byte[] file(List<String[]> rows) throws IOException {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(reader.template())); var out = new ByteArrayOutputStream()) {
            int i = 1;
            for (String[] values : rows) {
                var row = book.getSheetAt(0).createRow(i++);
                for (int c = 0; c < values.length; c++) row.createCell(c).setCellValue(values[c]);
            }
            book.write(out); return out.toByteArray();
        }
    }
    private ImportSubmissionVO run(String key, byte[] bytes) { return service.submit(42L, 7L, key, bytes, "test.xlsx", ImportExcelReader.MIME); }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }

    @Test @DisplayName("完整上传创建草稿与默认SKU，审计和来源回填同事务持久化")
    void success() throws Exception {
        assertTrue(AopUtils.isAopProxy(writer)); assertTrue(AopUtils.isAopProxy(transactions));
        String[] data = row("item-1"); data[3] = "900002";
        var result = run("request-1", file(Collections.singletonList(data))).batch();
        assertEquals("SUCCEEDED", result.status()); assertEquals(1, result.successSpuCount());
        assertEquals(1, result.successRowCount()); assertEquals(0, result.unprocessedSpuCount());
        assertEquals(1, count("product_spu")); assertEquals(1, count("product_sku"));
        assertEquals(0, jdbc.queryForObject("SELECT status FROM product_spu", Integer.class));
        assertEquals(7L, jdbc.queryForObject("SELECT created_by FROM product_spu", Long.class));
        assertEquals("[]", jdbc.queryForObject("SELECT CAST(spec_values AS CHAR) FROM product_sku", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT is_default FROM product_sku", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM import_item_owner o JOIN product_spu s ON s.id=o.spu_id JOIN import_group_result g ON g.id=o.group_result_id", Integer.class));
        assertTrue(Files.exists(Path.of(jdbc.queryForObject("SELECT file_url FROM import_batch", String.class))));
    }

    @Test @DisplayName("同请求键同文件重放，不同指纹冲突；新请求同文件仅提示但来源拒绝重复建档")
    void idempotency() throws Exception {
        byte[] data = file(Collections.singletonList(row("a")));
        var first = run("idem", data);
        var replay = run("idem", data);
        assertTrue(replay.replayed()); assertEquals(first.batch().batchId(), replay.batch().batchId());
        var conflict = assertThrows(BizException.class, () -> run("idem", file(Collections.singletonList(row("b")))));
        assertEquals(ProductErrorCode.IDEMPOTENCY_KEY_CONFLICT.getCode(), conflict.getCode());
        var duplicate = run("new-idem", data).batch();
        assertEquals("FAILED", duplicate.status()); assertEquals(List.of("FILE_ALREADY_IMPORTED"), duplicate.warnings());
        assertEquals("IMPORT_ITEM_DUPLICATED", queries.groups(duplicate.batchId(), 42L, 1, 20).getRecords().getFirst().errorCode());
        assertEquals(1, count("product_spu")); assertEquals(2, count("import_batch"));
    }

    @Test @DisplayName("同组一行价格错误则全组失败，其他组继续，修正后新请求可以成功")
    void partialAndRepair() throws Exception {
        String[] good = row("bad-group"), bad = row("BAD-GROUP"); bad[7] = "十二元";
        var batch = run("partial", file(List.of(good, bad, row("good-group")))).batch();
        assertEquals("PARTIAL", batch.status()); assertEquals(2, batch.totalSpuCount());
        assertEquals(2, batch.failedRowCount()); assertEquals(1, batch.successRowCount());
        var errors = queries.errors(batch.batchId(), 42L, 1, 20);
        assertEquals(2, errors.getTotal());
        assertEquals("GROUP_FAILED", errors.getRecords().get(0).errorCode());
        assertEquals("SKU_PRICE_INVALID", errors.getRecords().get(1).errorCode());
        var repair = run("repair", file(Collections.singletonList(row("bad-group")))).batch();
        assertEquals("SUCCEEDED", repair.status()); assertEquals(2, count("import_item_owner"));
    }

    @Test @DisplayName("无法归组计失败行不计商品组，报告以文本输出防公式注入，跨owner不可查询")
    void ungroupedAndReport() throws Exception {
        var batch = run("ungrouped", file(List.of(row("=1+1"), row("good")))).batch();
        assertEquals("PARTIAL", batch.status()); assertEquals(1, batch.totalSpuCount());
        assertEquals(0, batch.failedSpuCount()); assertEquals(1, batch.ungroupedFailedRowCount());
        assertThrows(BizException.class, () -> queries.detail(batch.batchId(), 43L));
        assertThrows(BizException.class, () -> queries.report(batch.batchId(), 43L));
        assertThrows(BizException.class, () -> queries.errors(batch.batchId(), 42L, 1, 101));
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(queries.report(batch.batchId(), 42L)))) {
            var cell = book.getSheetAt(0).getRow(1).getCell(1);
            assertEquals(CellType.STRING, cell.getCellType()); assertEquals("=1+1", cell.getStringCellValue());
        }
    }

    @Test @DisplayName("损坏文件写FAILED批次但不生成任何组或商品")
    void fileRejection() {
        var batch = run("invalid", "not xlsx".getBytes()).batch();
        assertEquals("FAILED", batch.status()); assertTrue(batch.errorSummary().startsWith("FILE_UNREADABLE"));
        assertEquals(0, count("import_group_result")); assertEquals(0, count("product_spu"));
    }

    @Test @DisplayName("第二批SKU失败回滚第一批、SPU及来源，批次中断且不伪造失败结果")
    void rollbackSecondBatch() throws Exception {
        jdbc.execute("CREATE TRIGGER fm007_fail_sku BEFORE INSERT ON product_sku FOR EACH ROW BEGIN IF NEW.price=13.37 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected second batch failure'; END IF; END");
        var rows = new ArrayList<String[]>();
        String values = java.util.stream.IntStream.range(0, 20).mapToObj(i -> "\"v" + i + "\"").collect(java.util.stream.Collectors.joining(","));
        for (int i = 0; i < 201; i++) {
            String[] row = row("many");
            row[5] = "[{\"name\":\"a\",\"values\":[" + values + "]},{\"name\":\"b\",\"values\":[" + values + "]}]";
            row[6] = "[{\"name\":\"a\",\"value\":\"v" + i / 20 + "\"},{\"name\":\"b\",\"value\":\"v" + i % 20 + "\"}]";
            if (i == 200) row[7] = "13.37";
            rows.add(row);
        }
        var batch = run("rollback", file(rows)).batch();
        assertEquals("INTERRUPTED", batch.status()); assertEquals(1, batch.unprocessedSpuCount());
        assertEquals(0, batch.failedSpuCount()); assertEquals(0, count("product_spu")); assertEquals(0, count("product_sku"));
        assertEquals(0, count("import_item_owner")); assertEquals(0, count("import_group_result"));
        jdbc.execute("DROP TRIGGER fm007_fail_sku");
        assertEquals("SUCCEEDED", run("retry-rollback", file(rows)).batch().status());
        assertEquals(201, count("product_sku"));
    }

    @Test @DisplayName("失败报告自身写入失败必须回滚失败组并中止后续导入")
    void failureWriterRollback() throws Exception {
        jdbc.execute("CREATE TRIGGER fm007_fail_report BEFORE INSERT ON import_row_error FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected report failure'");
        String[] invalid = row("bad"); invalid[7] = "bad";
        var batch = run("report-fail", file(List.of(invalid, row("good")))).batch();
        assertEquals("INTERRUPTED", batch.status()); assertEquals(2, batch.unprocessedSpuCount());
        assertEquals(0, count("import_group_result")); assertEquals(0, count("import_row_error")); assertEquals(0, count("product_spu"));
    }

    @RepeatedTest(3) @DisplayName("并发相同请求键只有一个批次；不同请求键相同商品只有一个成功来源")
    void concurrency() throws Exception {
        byte[] data = file(Collections.singletonList(row("concurrent")));
        var same = parallel(() -> run("same", data), () -> run("same", data));
        assertEquals(same.get(0).batch().batchId(), same.get(1).batch().batchId());
        assertEquals(1, same.stream().filter(ImportSubmissionVO::replayed).count());
        assertEquals(1, count("import_batch")); assertEquals(1, count("product_spu"));
        byte[] other = file(Collections.singletonList(row("second")));
        var competing = parallel(() -> run("one", other), () -> run("two", other));
        assertEquals(Set.of("SUCCEEDED", "FAILED"), new HashSet<>(competing.stream().map(r -> r.batch().status()).toList()));
        assertEquals(2, count("product_spu")); assertEquals(2, count("import_item_owner"));
    }
    private List<ImportSubmissionVO> parallel(Callable<ImportSubmissionVO> a, Callable<ImportSubmissionVO> b) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CyclicBarrier(2);
            var first = executor.submit(() -> { ready.await(10, TimeUnit.SECONDS); return a.call(); });
            var second = executor.submit(() -> { ready.await(10, TimeUnit.SECONDS); return b.call(); });
            return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        }
    }

    @Test @DisplayName("无进展批次按持久化结果标记中断，最近有心跳的不误判")
    void recovery() {
        var stale = transactions.create(42L, 7L, "stale", "a".repeat(64), "file.xlsx", "b".repeat(64));
        transactions.start(stale.getId(), 42L, 2, 3, "test-path");
        jdbc.update("UPDATE import_batch SET updated_at=DATE_SUB(NOW(3), INTERVAL 31 MINUTE) WHERE id=?", stale.getId());
        transactions.interruptStale(stale.getId(), 42L, LocalDateTime.now().minusMinutes(30));
        assertEquals("INTERRUPTED", queries.detail(stale.getId(), 42L).status());
        assertEquals(2, queries.detail(stale.getId(), 42L).unprocessedSpuCount());
        var active = transactions.create(42L, 7L, "active", "a".repeat(64), "file.xlsx", "b".repeat(64));
        transactions.interruptStale(active.getId(), 42L, LocalDateTime.now().minusMinutes(30));
        assertEquals("PENDING", queries.detail(active.getId(), 42L).status());
    }

    @Test @DisplayName("类目品牌各业务拒绝不写商品，失败计数与行报告一致")
    void referenceFailures() throws Exception {
        jdbc.update("UPDATE product_brand SET status=0 WHERE id=900002");
        String[] disabled = row("disabled"); disabled[3] = "900002";
        String[] missing = row("missing"); missing[2] = "999999";
        String[] missingBrand = row("missing-brand"); missingBrand[3] = "999999";
        var batch = run("bad-refs", file(List.of(disabled, missing, missingBrand))).batch();
        assertEquals("FAILED", batch.status()); assertEquals(3, batch.failedSpuCount());
        assertEquals(Set.of("BRAND_DISABLED", "CATEGORY_NOT_FOUND", "BRAND_NOT_FOUND"),
                new HashSet<>(queries.groups(batch.batchId(), 42L, 1, 20).getRecords().stream().map(ImportGroupVO::errorCode).toList()));
        jdbc.update("UPDATE product_brand SET status=1 WHERE id=900002");
        jdbc.update("UPDATE product_category_brand SET deleted=id WHERE id=900003");
        assertEquals("FAILED", run("unbound", file(Collections.singletonList(disabled))).batch().status());
        assertEquals(0, count("product_spu")); assertEquals(0, count("import_item_owner"));
    }

    @Test @DisplayName("事务内复核拒绝预检后禁用的类目，事务外成功不等于可以落库")
    void lockedRecheck() throws Exception {
        var parsed = reader.read(file(Collections.singletonList(row("recheck"))), "test.xlsx", ImportExcelReader.MIME);
        var group = new com.flowmart.product.support.ImportRowGrouper().group(parsed.rows()).groups().getFirst();
        var batch = transactions.create(42L, 7L, "recheck", "a".repeat(64), "file.xlsx", "b".repeat(64));
        transactions.start(batch.getId(), 42L, 1, 1, "test-path");
        jdbc.update("UPDATE product_category SET status=0 WHERE id=900001");
        var error = assertThrows(ImportGroupRejectedException.class, () -> writer.write(batch.getId(), 42L, 7L, group));
        assertEquals("CATEGORY_DISABLED", error.code()); assertEquals(0, count("product_spu"));
        assertEquals(0, count("import_item_owner"));
    }

    @Test @DisplayName("同标识不同归属可独立导入，全部无法归组不能误判成功")
    void ownerIsolationAndOnlyBadRows() throws Exception {
        byte[] data = file(Collections.singletonList(row("shared")));
        assertEquals("SUCCEEDED", run("one-owner", data).batch().status());
        assertEquals("SUCCEEDED", service.submit(43L, 8L, "one-owner", data, "test.xlsx", ImportExcelReader.MIME).batch().status());
        var bad = run("only-invalid", file(List.of(row(""), row("非法标识")))).batch();
        assertEquals("FAILED", bad.status()); assertEquals(0, bad.totalSpuCount());
        assertEquals(0, bad.failedSpuCount()); assertEquals(2, bad.failedRowCount()); assertEquals(2, bad.ungroupedFailedRowCount());
    }

    @Test @DisplayName("HTTP上传和下载可用，缺少文件或幂等头返回统一参数错误")
    void http() throws Exception {
        byte[] bytes = file(Collections.singletonList(row("http")));
        var upload = new MockMultipartFile("file", "test.xlsx", ImportExcelReader.MIME, bytes);
        mvc.perform(multipart("/api/product/admin/imports").file(upload).header("Idempotency-Key", "http"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.batch.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.batch.batchId").isString());
        mvc.perform(multipart("/api/product/admin/imports").file(upload)).andExpect(jsonPath("$.code").value(10001));
        mvc.perform(multipart("/api/product/admin/imports").header("Idempotency-Key", "x")).andExpect(jsonPath("$.code").value(10001));
        mvc.perform(get("/api/product/admin/imports/template")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/product/admin/imports/not-a-number")).andExpect(jsonPath("$.code").value(10001));
    }
}
