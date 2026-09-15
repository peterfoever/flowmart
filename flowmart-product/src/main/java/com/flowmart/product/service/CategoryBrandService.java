package com.flowmart.product.service;

import com.flowmart.product.dto.ReplaceCategoryBrandsDTO;
import com.flowmart.product.entity.ProductBrand;
import com.flowmart.product.vo.CategoryBrandVO;

import java.util.List;

public interface CategoryBrandService {
    void replaceCategoryBrands(Long categoryId, ReplaceCategoryBrandsDTO request);

    List<CategoryBrandVO> getCategoryBrands(Long categoryId);
}
