package com.flowmart.product.controller;

import com.flowmart.common.result.R;
import com.flowmart.product.dto.CreateSpuDTO;
import com.flowmart.product.service.SpuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
