package com.flowmart.product.service;

import com.flowmart.product.dto.CreateCategoryDTO;
import com.flowmart.product.entity.ProductCategory;
import com.flowmart.product.vo.CategoryDetailVO;
import com.flowmart.product.vo.CategoryTreeVO;

import java.util.List;

public interface CategoryService {
    Long creat(CreateCategoryDTO createCategoryDTO);
    List<CategoryTreeVO> listChildren(Long parentId);
}
