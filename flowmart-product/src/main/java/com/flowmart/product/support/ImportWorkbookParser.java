package com.flowmart.product.support;

import com.flowmart.product.dto.imports.*;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.ss.util.CellReference;
import javax.xml.stream.*;
import java.io.*;
import java.util.*;

/** 流式读取sheet，避免先构建任意大小的Workbook对象树再检查1000行上限。 */
final class ImportWorkbookParser {
    List<RawImportRow> read(byte[] bytes) throws IOException,
            org.apache.poi.openxml4j.exceptions.OpenXML4JException, XMLStreamException {
        try (var pkg = OPCPackage.open(new ByteArrayInputStream(bytes))) {
            XSSFReader reader = new XSSFReader(pkg);
            List<String> strings;
            try (var input = reader.getSharedStringsData()) { strings = sharedStrings(input); }
            var sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            if (!sheets.hasNext()) throw fail("SHEET_COUNT_INVALID", "必须且只能有一个Sheet");
            List<RawImportRow> rows;
            try (var input = sheets.next()) {
                if (sheets.hasNext()) throw fail("SHEET_COUNT_INVALID", "必须且只能有一个Sheet");
                if (!ImportExcelReader.SHEET.equals(sheets.getSheetName())) throw fail("SHEET_NAME_MISMATCH", "Sheet名称必须为" + ImportExcelReader.SHEET);
                rows = sheet(input, strings);
            }
            if (sheets.hasNext()) throw fail("SHEET_COUNT_INVALID", "必须且只能有一个Sheet");
            return rows;
        }
    }

    private XMLStreamReader xml(InputStream input) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setXMLResolver((publicId, systemId, baseURI, namespace) -> { throw new XMLStreamException("External entity forbidden"); });
        return factory.createXMLStreamReader(input);
    }
    private List<String> sharedStrings(InputStream input) throws XMLStreamException {
        if (input == null) return List.of();
        List<String> values = new ArrayList<>();
        XMLStreamReader xml = xml(input);
        StringBuilder current = null;
        int phoneticDepth = 0;
        try {
            while (xml.hasNext()) {
                int event = xml.next();
                if (event == XMLStreamConstants.DTD) throw new XMLStreamException("DTD forbidden");
                if (event == XMLStreamConstants.START_ELEMENT) {
                    switch (xml.getLocalName()) {
                        case "si" -> {
                            if (values.size() >= 10000) throw fail("FILE_TOO_LARGE", "共享字符串过多，请使用干净模板");
                            current = new StringBuilder();
                        }
                        case "rPh" -> phoneticDepth++;
                        case "t" -> {
                            String text = text(xml);
                            if (current != null && phoneticDepth == 0) { current.append(text); length(current.length()); }
                        }
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if ("rPh".equals(xml.getLocalName())) phoneticDepth--;
                    if ("si".equals(xml.getLocalName())) {
                        if (current == null) throw new XMLStreamException("Invalid shared string");
                        values.add(current.toString()); current = null;
                    }
                }
            }
        } finally { xml.close(); }
        return values;
    }

    private List<RawImportRow> sheet(InputStream input, List<String> strings) throws XMLStreamException {
        XMLStreamReader xml = xml(input);
        List<RawImportRow> rows = new ArrayList<>();
        Map<Integer, RawCell> cells = null;
        int rowNum = 0, previousRow = 0;
        boolean header = false;
        try {
            while (xml.hasNext()) {
                int event = xml.next();
                if (event == XMLStreamConstants.DTD) throw new XMLStreamException("DTD forbidden");
                if (event == XMLStreamConstants.START_ELEMENT) {
                    switch (xml.getLocalName()) {
                        case "row" -> {
                            rowNum = integer(xml.getAttributeValue(null, "r"));
                            if (cells != null || rowNum <= previousRow || rowNum > 1048576) throw new XMLStreamException("Invalid row number");
                            previousRow = rowNum; cells = new HashMap<>();
                        }
                        case "c" -> {
                            if (cells == null) throw new XMLStreamException("Cell outside row");
                            String ref = xml.getAttributeValue(null, "r");
                            if (ref == null || !ref.matches("[A-Z]{1,3}[1-9][0-9]{0,6}")) throw new XMLStreamException("Invalid cell reference");
                            CellReference cellRef = new CellReference(ref);
                            if (cellRef.getRow() + 1 != rowNum) throw new XMLStreamException("Mismatched row reference");
                            int column = cellRef.getCol();
                            RawCell cell = cell(xml, strings);
                            if (column >= ImportColumns.COLUMN_COUNT) {
                                if (rowNum == 1 || !cell.isBlank()) throw fail("HEADER_MISMATCH", "包含模板以外的列");
                            } else if (cells.putIfAbsent(column, cell) != null) throw new XMLStreamException("Duplicate cell");
                        }
                        case "mergeCell" -> throw fail("HEADER_MISMATCH", "模板不允许合并单元格");
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT && "row".equals(xml.getLocalName())) {
                    if (cells == null) throw new XMLStreamException("Invalid row");
                    RawImportRow row = new RawImportRow(rowNum, cells);
                    if (rowNum == 1) {
                        for (int c = 0; c < ImportColumns.COLUMN_COUNT; c++) {
                            var cell = row.cell(c);
                            if (cell.type() != com.flowmart.product.enums.RawCellType.TEXT || !ImportExcelReader.HEADERS.get(c).equals(cell.rawValue())) {
                                throw fail("HEADER_MISMATCH", "表头第" + (c + 1) + "列必须为" + ImportExcelReader.HEADERS.get(c));
                            }
                        }
                        header = true;
                    } else if (!row.allBlank()) {
                        if (rows.size() >= 1000) throw fail("TOO_MANY_ROWS", "最多1000条非空数据行");
                        rows.add(row);
                    }
                    cells = null;
                }
            }
        } finally { xml.close(); }
        if (!header) throw fail("HEADER_MISMATCH", "缺少模板表头");
        if (rows.isEmpty()) throw fail("EMPTY_FILE", "文件至少需要一条非空数据行");
        return rows;
    }

    private RawCell cell(XMLStreamReader xml, List<String> strings) throws XMLStreamException {
        String type = xml.getAttributeValue(null, "t");
        String value = null;
        StringBuilder inline = new StringBuilder();
        boolean formula = false;
        int depth = 1;
        while (xml.hasNext() && depth > 0) {
            int event = xml.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String tag = xml.getLocalName();
                if (List.of("f", "v", "t").contains(tag)) {
                    String content = text(xml);
                    if ("f".equals(tag)) formula = true;
                    else if ("v".equals(tag)) value = content;
                    else { inline.append(content); length(inline.length()); }
                } else depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) depth--;
            else if (event == XMLStreamConstants.DTD) throw new XMLStreamException("DTD forbidden");
        }
        if (depth != 0) throw new XMLStreamException("Unclosed cell");
        if (formula) return RawCell.formula();
        if ("inlineStr".equals(type)) return RawCell.text(inline.toString());
        if ("s".equals(type)) {
            int index = integer(value);
            if (index < 0 || index >= strings.size()) throw new XMLStreamException("Invalid shared string index");
            return RawCell.text(strings.get(index));
        }
        if ("str".equals(type)) return RawCell.text(value);
        if ("b".equals(type) || "e".equals(type) || "d".equals(type)) return RawCell.error();
        if (type != null && !"n".equals(type)) throw new XMLStreamException("Unknown cell type");
        return value == null ? RawCell.blank() : RawCell.number(value);
    }
    private int integer(String value) throws XMLStreamException {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { throw new XMLStreamException("Invalid integer", e); }
    }
    private String text(XMLStreamReader xml) throws XMLStreamException {
        StringBuilder result = new StringBuilder();
        while (xml.hasNext()) {
            int event = xml.next();
            if (event == XMLStreamConstants.END_ELEMENT) return result.toString();
            if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                length(result.length() + xml.getTextLength()); result.append(xml.getText());
            } else if (event == XMLStreamConstants.START_ELEMENT || event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) {
                throw new XMLStreamException("Invalid text content");
            }
        }
        throw new XMLStreamException("Unclosed text");
    }
    private void length(int length) { if (length > 4096) throw fail("CELL_TOO_LONG", "单元格超过4096字符"); }
    private ImportExcelReader.FileRejected fail(String code, String message) { return new ImportExcelReader.FileRejected(code, message); }
}
