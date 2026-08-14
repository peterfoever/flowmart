package com.flowmart.product.controller;

import com.flowmart.common.result.R;
import com.flowmart.product.dto.CreateCategoryDTO;
import com.flowmart.product.service.CategoryService;
import com.flowmart.product.vo.CategoryDetailVO;
import com.flowmart.product.vo.CategoryTreeVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController()
@RequestMapping("api/product")
public class ProductCategoryController {

    private final CategoryService categoryService;

    public ProductCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping("/categories")
    public R<CategoryDetailVO> createCategory(@RequestBody @Valid CreateCategoryDTO request) {
        categoryService.create(request);
        return R.ok();
    }

    @GetMapping("/categories?parentId=")
    public List<CategoryTreeVO> getCategoryByParentId(@RequestParam("parentId") Long parentId) {
        List<CategoryTreeVO> categoryTreeVOS = categoryService.listChildren(parentId);
        return categoryTreeVOS;
    }
}
