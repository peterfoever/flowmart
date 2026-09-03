package com.flowmart.product.service;

import com.flowmart.product.command.DeleteCategoryCommand;
import com.flowmart.product.dto.CreateCategoryDTO;

import com.flowmart.product.dto.MoveCategoryDTO;
import com.flowmart.product.vo.CategoryTreeVO;

import java.util.List;

public interface CategoryService {
    Long create(CreateCategoryDTO createCategoryDTO);
    List<CategoryTreeVO> listChildren(Long parentId);

    List<CategoryTreeVO> listFrontTree();

    List<CategoryTreeVO> listAdminTree();

    void delete(DeleteCategoryCommand command);
    /**
     * 移动类目
     *
     * @param id      待移动的类目 ID
     * @param request 移动请求（含目标父类目 ID）
     */
    void move(Long id, MoveCategoryDTO request);
}
