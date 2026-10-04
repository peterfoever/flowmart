package com.flowmart.product.controller;

import com.flowmart.common.result.R;
import com.flowmart.product.dto.SkuGenerateDTO;
import com.flowmart.product.service.SkuService;
import com.flowmart.product.service.impl.SkuGenerateService;
import com.flowmart.product.vo.SkuDetailVO;
import com.flowmart.product.vo.SkuListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/product/admin")
@RequiredArgsConstructor
public class SkuController {
    private final SkuGenerateService skuGenerateService;
    private final SkuService skuService;

    @Operation(summary = "生成 SKU",
            description = "按 SPU 的规格定义生成 SKU 组合；一个 SPU 只允许初始化生成一次")

    @PostMapping("/spus/{spuId}/skus/generate")
    public R<List<Long>> generate(@Parameter(description = "SPU ID") @Min(1) @PathVariable("spuId") Long spuId,
                                 @Valid @RequestBody SkuGenerateDTO dto) {

        return R.ok(skuGenerateService.generate(spuId, dto, 0L));
    }

    @GetMapping("/spus/{spuId}/skus")
    public R<List<SkuListVO>> listSku(@Parameter(description = "SPU ID") @Min(1) @PathVariable("spuId") Long spuId) {
        return R.ok(skuService.listSku(spuId));
    }

    @GetMapping("/skus/{id}")
    public R<SkuDetailVO> detailSku(@Parameter(description = "SKU ID") @Min(1) @PathVariable("id") Long skuId) {
        return R.ok(skuService.detailSku(skuId));
    }
}

