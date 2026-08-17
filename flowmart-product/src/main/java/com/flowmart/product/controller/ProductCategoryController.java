package com.flowmart.product.controller;

import com.flowmart.common.result.R;
import com.flowmart.product.dto.CreateCategoryDTO;
import com.flowmart.product.service.CategoryService;
import com.flowmart.product.vo.CategoryTreeVO;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController()
@RequestMapping("/api/product")
@Slf4j
public class ProductCategoryController {

    private final CategoryService categoryService;

    public ProductCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping("/categories")
    public R<Long> createCategory(@RequestBody @Valid CreateCategoryDTO request) {
        return R.ok(categoryService.create(request));
    }

    @GetMapping("/categories")
    public R<List<CategoryTreeVO>> getCategoryByParentId(@RequestParam("parentId") Long parentId) {
        List<CategoryTreeVO> categoryTreeVOS = categoryService.listChildren(parentId);
        return R.ok(categoryTreeVOS);
    }

    @GetMapping("/categories/tree")
    public R<List<CategoryTreeVO>> getFrontTree(){
        log.info("请求前台类目树");
        List<CategoryTreeVO> categoryTreeVOS = categoryService.listFrontTree();
        return R.ok(categoryTreeVOS);
    }

    @GetMapping("/admin/categories/tree")
    public R<List<CategoryTreeVO>> getAdminTree(){
        log.info("请求后台类目树");
        List<CategoryTreeVO> categoryTreeVOS = categoryService.listAdminTree();
        return R.ok(categoryTreeVOS);
    }
}
