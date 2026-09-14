package com.flowmart.product.controller;


import com.flowmart.common.result.PageResult;
import com.flowmart.common.result.R;
import com.flowmart.product.dto.*;
import com.flowmart.product.service.BrandService;
import com.flowmart.product.service.CategoryBrandService;
import com.flowmart.product.vo.BrandVO;
import com.flowmart.product.vo.CategoryBrandVO;
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
public class BrandController {
    private final BrandService brandService;
    private final CategoryBrandService categoryBrandService;

    @PostMapping("/brands")
    public R<Long> createBrand(@Valid @RequestBody CreateBrandDTO request) {
        Long createBrand = brandService.createBrand(request);
        return R.ok(createBrand);
    }

    @PutMapping("/brands/{id}")
    public R<Void> updateBrand(@PathVariable @Min(1) Long id, @Valid @RequestBody UpdateBrandDTO request) {
        brandService.updateBrand(id, request);
        return R.ok();
    }

    //
//    @DeleteMapping("/brands/{id}")
//    public R<Void> deleteBrand(@PathVariable @Min(1) Long id) {
//
//    }

    @GetMapping("/brands")
    public R<PageResult<BrandVO>> listBrands(@Valid BrandQueryDTO query) {
        log.info("分页查询品牌: name={}, initial={}, status={}, page={}, size={}",
                query.getName(), query.getInitial(), query.getStatus(),
                query.getPage(), query.getSize());
        return R.ok(brandService.pageBrands(query));
    }

    @GetMapping("/brands/{id}")
    public R<BrandVO> getBrand(@PathVariable @Min(1) Long id) {
        log.info("查询品牌详情: id={}", id);
        BrandVO brand = brandService.getBrand(id);
        return R.ok(brand);
    }

    @PatchMapping("/brands/{id}/status")
    public R<Void> updateStatus(@PathVariable @Min(1) Long id,
                                @Valid @RequestBody UpdateBrandStatusDTO request) {
        brandService.updateBrandStatus(id, request);
        return R.ok();
    }

    @GetMapping("/categories/{categoryId}/brands")
    public R<List<CategoryBrandVO>> listCategoryBrands(
            @PathVariable @Min(1) Long categoryId) {
        List<CategoryBrandVO> categoryBrands = categoryBrandService.getCategoryBrands(categoryId);
        return R.ok(categoryBrands);
    }

    @PutMapping("/categories/{categoryId}/brands")
    public R<Void> replaceCategoryBrands(
            @PathVariable @Min(1) Long categoryId,
            @Valid @RequestBody ReplaceCategoryBrandsDTO request) {
        categoryBrandService.replaceCategoryBrands(categoryId,request);
        return R.ok();
    }
}
