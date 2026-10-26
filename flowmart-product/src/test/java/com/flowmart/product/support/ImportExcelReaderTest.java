package com.flowmart.product.support;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.util.function.Consumer;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class ImportExcelReaderTest {
    private final ImportExcelReader reader = new ImportExcelReader();
    private byte[] file(Consumer<Workbook> edit) throws IOException {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(reader.template())); var out = new ByteArrayOutputStream()) {
            edit.accept(book); book.write(out); return out.toByteArray();
        }
    }
    private void valid(Row row, String key) {
        String[] values = {key, "商品", "1234567890123456789", "", "main.png", "", "", "12.30", "sku.png"};
        for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
    }
    private void rejected(byte[] bytes, String code) {
        var result = reader.read(bytes, "file.xlsx", ImportExcelReader.MIME);
        assertNotNull(result.fileLevelError()); assertEquals(code, result.fileLevelError().errorCode());
        assertTrue(result.rows().isEmpty()); assertTrue(result.rowErrors().isEmpty());
    }
    @Test @DisplayName("模板可读取，文本ID保留精度，跳过空行但不改真实行号")
    void templateAndRows() throws Exception {
        byte[] bytes = file(book -> { valid(book.getSheetAt(0).createRow(1), "a"); valid(book.getSheetAt(0).createRow(8), "b"); });
        var result = reader.read(bytes, "TEST.XLSX", ImportExcelReader.MIME);
        assertNull(result.fileLevelError()); assertEquals(2, result.rows().size());
        assertEquals(9, result.rows().get(1).rowNum()); assertEquals(1234567890123456789L, result.rows().getFirst().categoryId());
    }
    @Test @DisplayName("默认模板没有数据时明确拒绝")
    void empty() throws Exception { rejected(reader.template(), "EMPTY_FILE"); rejected(new byte[0], "EMPTY_FILE"); }
    @Test @DisplayName("数值ID不转字符串，公式不读取缓存，错误与布尔值不当空行")
    void types() throws Exception {
        var result = reader.read(file(book -> {
            var sheet = book.getSheetAt(0);
            valid(sheet.createRow(1), "a"); sheet.getRow(1).getCell(2).setCellValue(1234567890123456789D);
            valid(sheet.createRow(2), "b"); sheet.getRow(2).getCell(7).setCellFormula("1+1");
            valid(sheet.createRow(3), "c"); sheet.getRow(3).getCell(4).setCellErrorValue(FormulaError.REF.getCode());
            valid(sheet.createRow(4), "d"); sheet.getRow(4).getCell(4).setCellValue(true);
        }), "file.xlsx", ImportExcelReader.MIME);
        assertNull(result.fileLevelError()); assertEquals(4, result.rowErrors().size());
        assertEquals("IMPORT_ID_NOT_TEXT", result.rowErrors().getFirst().errorCode());
        assertEquals("CELL_FORMULA_NOT_ALLOWED", result.rowErrors().get(1).errorCode());
    }
    @Test @DisplayName("多Sheet、错误名称、表头及模板外列整份拒绝")
    void layout() throws Exception {
        rejected(file(book -> book.createSheet("extra")), "SHEET_COUNT_INVALID");
        rejected(file(book -> book.setSheetName(0, "错误名称")), "SHEET_NAME_MISMATCH");
        rejected(file(book -> book.getSheetAt(0).getRow(0).getCell(0).setCellValue("wrong")), "HEADER_MISMATCH");
        rejected(file(book -> book.getSheetAt(0).createRow(1).createCell(9).setCellValue("extra")), "HEADER_MISMATCH");
    }
    @Test @DisplayName("1000行接受，1001行整份拒绝，不泄露部分结果")
    void rowLimit() throws Exception {
        var good = file(book -> { for (int i = 1; i <= 1000; i++) valid(book.getSheetAt(0).createRow(i), "item" + i); });
        assertEquals(1000, reader.read(good, "file.xlsx", ImportExcelReader.MIME).rows().size());
        rejected(file(book -> { for (int i = 1; i <= 1001; i++) valid(book.getSheetAt(0).createRow(i), "item" + i); }), "TOO_MANY_ROWS");
    }
    @Test @DisplayName("超长单元格和5MiB文件拒绝")
    void lengths() throws Exception {
        rejected(file(book -> book.getSheetAt(0).createRow(1).createCell(0).setCellValue("x".repeat(4097))), "CELL_TOO_LONG");
        rejected(new byte[ImportExcelReader.MAX_BYTES + 1], "FILE_TOO_LARGE");
    }
    @ParameterizedTest @ValueSource(strings = {"a.xls", "a.csv", "a.xlsx.exe"})
    @DisplayName("伪装扩展名不接受")
    void extensions(String name) throws Exception {
        assertEquals("FILE_TYPE_INVALID", reader.read(reader.template(), name, ImportExcelReader.MIME).fileLevelError().errorCode());
    }
    @Test @DisplayName("损坏ZIP和错误MIME拒绝")
    void invalidFile() throws Exception {
        rejected("not zip".getBytes(), "FILE_UNREADABLE");
        assertEquals("FILE_TYPE_INVALID", reader.read(reader.template(), "a.xlsx", "application/octet-stream").fileLevelError().errorCode());
    }
    @Test @DisplayName("累计实际解压字节，拦截高压缩ZIP")
    void zipBomb() throws Exception {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("huge"));
            byte[] block = new byte[1024 * 1024];
            for (int i = 0; i < 51; i++) zip.write(block);
        }
        rejected(out.toByteArray(), "FILE_TOO_LARGE");
    }

    @Test @DisplayName("XML外部实体及重复行号拒绝，不读取外部资源")
    void maliciousXml() throws Exception {
        byte[] normal = file(book -> valid(book.getSheetAt(0).createRow(1), "good"));
        String dtd = "<!DOCTYPE worksheet [<!ENTITY leak SYSTEM 'file:///definitely-not-readable-by-import'>]>";
        rejected(replaceSheet(normal, xml -> xml.replaceFirst("\\?>", "?>" + dtd)), "FILE_UNREADABLE");
        rejected(replaceSheet(normal, xml -> xml.replace("r=\"2\"", "r=\"1\"")), "FILE_UNREADABLE");
    }

    @Test @DisplayName("大量格式空行流式跳过，保留尾部数据真实行号")
    void sparseRows() throws Exception {
        byte[] normal = file(book -> valid(book.getSheetAt(0).createRow(20001), "tail"));
        byte[] sparse = replaceSheet(normal, xml -> {
            StringBuilder blank = new StringBuilder();
            for (int i = 2; i <= 20000; i++) blank.append("<row r=\"").append(i).append("\"/>");
            return xml.replace("<row r=\"20002\"", blank + "<row r=\"20002\"");
        });
        var result = reader.read(sparse, "file.xlsx", ImportExcelReader.MIME);
        assertNull(result.fileLevelError()); assertEquals(20002, result.rows().getFirst().rowNum());
    }
    private byte[] replaceSheet(byte[] file, java.util.function.UnaryOperator<String> edit) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var input = new ZipInputStream(new ByteArrayInputStream(file)); var zip = new ZipOutputStream(out)) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                byte[] bytes = input.readAllBytes();
                if (entry.getName().equals("xl/worksheets/sheet1.xml")) bytes = edit.apply(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                zip.putNextEntry(new ZipEntry(entry.getName())); zip.write(bytes); zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
