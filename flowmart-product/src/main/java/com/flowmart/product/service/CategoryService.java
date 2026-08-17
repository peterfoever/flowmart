package com.flowmart.product.service;

import com.flowmart.product.dto.CreateCategoryDTO;

import com.flowmart.product.vo.CategoryTreeVO;

import java.util.List;

public interface CategoryService {
    Long create(CreateCategoryDTO createCategoryDTO);
    List<CategoryTreeVO> listChildren(Long parentId);

    List<CategoryTreeVO> listFrontTree();

    List<CategoryTreeVO> listAdminTree();
}
