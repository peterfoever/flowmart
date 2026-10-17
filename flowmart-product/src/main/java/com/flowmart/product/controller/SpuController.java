package com.flowmart.product.controller;

import com.flowmart.common.result.PageResult;
import com.flowmart.common.result.R;
import com.flowmart.product.dto.CreateSpuDTO;
import com.flowmart.product.dto.SpuQueryDTO;
import com.flowmart.product.dto.SpuPublishDTO;
import com.flowmart.product.dto.UpdateSpuDTO;
import com.flowmart.product.service.SpuService;
import com.flowmart.product.vo.SpuDetailVO;
import com.flowmart.product.vo.SpuListVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @GetMapping("/spus")
    public R<PageResult<SpuListVO>> page(@Valid @ModelAttribute SpuQueryDTO request) {
        return R.ok(spuService.page(request));
    }

    @PostMapping("/spus/{id}/publish")
    public R<Void> publish(@PathVariable @NotNull @Min(1) Long id,
                           @Valid @RequestBody SpuPublishDTO request) {
        spuService.publish(id, request.getVersion());
        return R.ok();
    }
}
