package com.flowmart.product.controller;

import com.flowmart.product.dto.CreateCategoryDTO;
import com.flowmart.product.service.CategoryService;
import com.flowmart.product.vo.CategoryDetailVO;
import com.flowmart.product.vo.CategoryTreeVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("api/product")
public class ProductCategoryController {

    private final CategoryService categoryService;

    public ProductCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping("/categories")
    public void createCategory(CreateCategoryDTO request) {
        categoryService.creat(request);
    }

    @GetMapping("/categories?parentId=")
    public List<CategoryTreeVO> getCategoryByParentId(@RequestParam("parentId") Long parentId) {
        List<CategoryTreeVO> categoryTreeVOS = categoryService.listChildren(parentId);
        return categoryTreeVOS;
    }
}
