package com.flowmart.product.controller;

import com.flowmart.common.exception.BizException;
import com.flowmart.common.result.*;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.service.importing.*;
import com.flowmart.product.support.ImportExcelReader;
import com.flowmart.product.vo.imports.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequestMapping("/api/product/admin/imports")
@RequiredArgsConstructor
public class ProductImportController {
    // 与项目现有接口一致，尚未接鉴权。禁止新增可由客户端伪造的ownerId请求参数。
    private static final Long SYSTEM_OWNER = 0L;
    private final ProductImportService service;
    private final ImportQueryService queries;
    private final ImportExcelReader reader;

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() throws IOException { return excel(reader.template(), "FM-007-template.xlsx"); }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<ImportSubmissionVO> upload(@RequestPart("file") MultipartFile file,
                                       @RequestHeader("Idempotency-Key") String key) throws IOException {
        if (file.getSize() > ImportExcelReader.MAX_BYTES) throw new BizException(ProductErrorCode.IMPORT_FILE_INVALID);
        byte[] bytes;
        try (var input = file.getInputStream()) { bytes = input.readNBytes(ImportExcelReader.MAX_BYTES + 1); }
        return R.ok(service.submit(SYSTEM_OWNER, SYSTEM_OWNER, key, bytes, file.getOriginalFilename(), file.getContentType()));
    }

    @GetMapping("/{id}")
    public R<ImportBatchVO> detail(@PathVariable Long id) { return R.ok(queries.detail(id, SYSTEM_OWNER)); }
    @GetMapping("/{id}/groups")
    public R<PageResult<ImportGroupVO>> groups(@PathVariable Long id, @RequestParam(defaultValue = "1") int pageNum,
                                              @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(queries.groups(id, SYSTEM_OWNER, pageNum, pageSize));
    }
    @GetMapping("/{id}/errors")
    public R<PageResult<ImportRowErrorVO>> errors(@PathVariable Long id, @RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(queries.errors(id, SYSTEM_OWNER, pageNum, pageSize));
    }
    @GetMapping("/{id}/errors.xlsx")
    public ResponseEntity<byte[]> report(@PathVariable Long id) throws IOException {
        return excel(queries.report(id, SYSTEM_OWNER), "FM-007-errors-" + id + ".xlsx");
    }
    private ResponseEntity<byte[]> excel(byte[] bytes, String name) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(ImportExcelReader.MIME))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(bytes);
    }
}
