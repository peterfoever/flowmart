package com.flowmart.product.service.impl;

import com.flowmart.product.entity.ProductCategory;
import com.flowmart.product.mapper.ProductCategoryMapper;
import com.flowmart.product.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {
    @Autowired
    private final ProductCategoryMapper productCategoryMapper;

    public CategoryServiceImpl(ProductCategoryMapper productCategoryMapper) {
        this.productCategoryMapper = productCategoryMapper;
    }

    @Override
    public void save(ProductCategory category) {
        productCategoryMapper.insert(category);
    }

    @Override
    public List<ProductCategory> findAllByParentId(Long parentId) {
        return List.of(productCategoryMapper.selectById(parentId));
    }
}
