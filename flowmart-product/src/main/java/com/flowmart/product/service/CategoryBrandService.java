package com.flowmart.product.service;

import com.flowmart.product.dto.ReplaceCategoryBrandsDTO;

public interface CategoryBrandService {
    void replaceCategoryBrands(Long categoryId, ReplaceCategoryBrandsDTO request);
}
