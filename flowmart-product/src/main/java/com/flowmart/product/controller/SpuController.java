package com.flowmart.product.controller;

import com.flowmart.common.result.R;
import com.flowmart.product.dto.CreateSpuDTO;
import com.flowmart.product.dto.UpdateSpuDTO;
import com.flowmart.product.service.SpuService;
import com.flowmart.product.vo.SpuDetailVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/product/admin")
@RequiredArgsConstructor
public class SpuController {

    private final SpuService spuService;

    @PostMapping("/spus/draft")
    public R<Long> createDraft(@Valid @RequestBody CreateSpuDTO createSpuDTO) {
        return R.ok(spuService.createDraft(createSpuDTO));
    }

    @GetMapping("/spus/{id}")
    public R<SpuDetailVO> getSpuDetail(@PathVariable @NotNull @Min(1) Long id) {
        return R.ok(spuService.getDetailById(id));
    }

    @PutMapping("/spus/{id}/draft")
    public R<Void> updateDraft(@PathVariable @NotNull @Min(1) Long id, @Valid @RequestBody UpdateSpuDTO request) {
        spuService.updateDraft(id, request);
        return R.ok();
    }
}
