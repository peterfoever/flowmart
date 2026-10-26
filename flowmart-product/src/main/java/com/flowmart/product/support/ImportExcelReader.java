package com.flowmart.product.support;

import com.flowmart.product.dto.imports.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;
import java.util.zip.ZipInputStream;

/** 有界、事务外读取；不计算公式、不使用格式化数字恢复已经丢失精度的ID。 */
@Component
public class ImportExcelReader {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final String MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String SHEET = "商品导入";
    public static final String VERSION = "FM007-v1";
    public static final List<String> HEADERS = List.of("importItemKey", "SPU名称", "类目ID", "品牌ID",
            "主图URL", "规格定义", "规格组合", "销售价", "SKU图片URL");

    public ImportParseResult read(byte[] bytes, String fileName, String mime) {
        if (bytes == null || bytes.length == 0) return reject("EMPTY_FILE", "文件为空");
        if (bytes.length > MAX_BYTES) return reject("FILE_TOO_LARGE", "文件不能超过5MiB");
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx") || !MIME.equals(mime)) {
            return reject("FILE_TYPE_INVALID", "仅接受xlsx扩展名及对应MIME类型");
        }
        try {
            checkZip(bytes);
            return new ImportRowValidator().validate(new ImportWorkbookParser().read(bytes));
        } catch (FileRejected e) {
            return reject(e.code, e.getMessage());
        } catch (IOException | javax.xml.stream.XMLStreamException | org.apache.poi.openxml4j.exceptions.OpenXML4JException
                 | org.apache.poi.openxml4j.exceptions.InvalidOperationException
                 | org.apache.poi.ooxml.POIXMLException | org.apache.poi.EncryptedDocumentException e) {
            return reject("FILE_UNREADABLE", "文件损坏、加密或不是有效xlsx，请重新下载模板");
        }
    }

    /** 在POI构造对象树前累计所有ZIP条目的实际解压字节，不信任ZIP头申报的大小。 */
    private void checkZip(byte[] bytes) throws IOException {
        long total = 0;
        Set<String> names = new HashSet<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            byte[] buffer = new byte[8192];
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (!names.add(entry.getName()) || names.size() > 1000) throw new FileRejected("FILE_UNREADABLE", "ZIP条目重复或过多");
                for (int n; (n = zip.read(buffer)) != -1;) {
                    total += n;
                    if (total > 50L * 1024 * 1024) throw new FileRejected("FILE_TOO_LARGE", "解压后文件超过50MiB");
                }
            }
        }
        if (!names.contains("[Content_Types].xml") || !names.contains("xl/workbook.xml")) {
            throw new FileRejected("FILE_UNREADABLE", "不是有效xlsx文件");
        }
    }

    public byte[] template() throws IOException {
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(SHEET);
            Row header = sheet.createRow(0);
            CellStyle text = workbook.createCellStyle();
            text.setDataFormat(workbook.createDataFormat().getFormat("@"));
            for (int i = 0; i < HEADERS.size(); i++) {
                header.createCell(i).setCellValue(HEADERS.get(i));
                sheet.setDefaultColumnStyle(i, text);
                sheet.setColumnWidth(i, 24 * 256);
            }
            sheet.createFreezePane(0, 1);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private ImportParseResult reject(String code, String message) {
        return new ImportParseResult(List.of(), List.of(), new FileLevelError(code, message));
    }
    static final class FileRejected extends RuntimeException {
        private final String code;
        FileRejected(String code, String message) { super(message); this.code = code; }
    }
}
