package com.flowmart.product.service;

import com.flowmart.product.entity.ProductCategory;

import java.util.List;

public interface CategoryService {
    void save(ProductCategory category);
    List<ProductCategory> findAllByParentId(Long parentId);
}
