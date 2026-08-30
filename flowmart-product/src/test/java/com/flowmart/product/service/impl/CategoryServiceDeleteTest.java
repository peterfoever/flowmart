package com.flowmart.product.service.impl;

import com.flowmart.common.exception.BizException;
import com.flowmart.product.checker.CategoryDeleteChecker;
import com.flowmart.product.command.DeleteCategoryCommand;
import com.flowmart.product.convert.CategoryConverter;
import com.flowmart.product.entity.ProductCategory;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.mapper.ProductCategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceDeleteTest {

    @Mock
    private ProductCategoryMapper categoryMapper;

    @Mock
    private CategoryConverter categoryConverter;

    @Mock
    private CategoryDeleteChecker deleteChecker;

    private CategoryServiceImpl categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryServiceImpl(categoryMapper, categoryConverter, List.of(deleteChecker));
    }

    @Test
    void shouldCascadeDeleteEntireSubtree() {
        ProductCategory root = category(1L, 0L, "食品", 1);
        ProductCategory child = category(2L, 1L, "零食", 2);
        ProductCategory grandchild = category(3L, 2L, "坚果", 3);
        givenDeleteData(root, List.of(child), List.of(root, child, grandchild));
        when(categoryMapper.batchLogicDelete(List.of(1L, 2L, 3L))).thenReturn(3);

        categoryService.delete(command(1L, true));

        verify(categoryMapper).batchLogicDelete(List.of(1L, 2L, 3L));
        verify(categoryMapper, never()).batchUpdateParentId(any(), anyLong());
        verify(categoryMapper, never()).batchDecreaseLevel(any());
    }

    @Test
    void shouldReparentChildrenDecreaseDescendantLevelAndDeleteOnlyCurrentCategory() {
        ProductCategory root = category(2L, 1L, "零食", 2);
        ProductCategory child = category(3L, 2L, "坚果", 3);
        ProductCategory grandchild = category(4L, 3L, "夏威夷果", 4);
        givenDeleteData(root, List.of(child), List.of(root, child, grandchild));
        when(categoryMapper.batchUpdateParentId(List.of(3L), 1L)).thenReturn(1);
        when(categoryMapper.batchDecreaseLevel(List.of(3L, 4L))).thenReturn(2);
        when(categoryMapper.batchLogicDelete(List.of(2L))).thenReturn(1);

        categoryService.delete(command(2L, false));

        verify(categoryMapper).batchUpdateParentId(List.of(3L), 1L);
        verify(categoryMapper).batchDecreaseLevel(List.of(3L, 4L));
        verify(categoryMapper).batchLogicDelete(List.of(2L));
    }

    @Test
    void shouldDeleteLeafWithoutEmptyBatchUpdates() {
        ProductCategory leaf = category(5L, 0L, "图书", 1);
        givenDeleteData(leaf, List.of(), List.of(leaf));
        when(categoryMapper.batchLogicDelete(List.of(5L))).thenReturn(1);

        categoryService.delete(command(5L, false));

        verify(categoryMapper, never()).batchUpdateParentId(any(), anyLong());
        verify(categoryMapper, never()).batchDecreaseLevel(any());
        verify(categoryMapper).batchLogicDelete(List.of(5L));
    }

    @Test
    void shouldStopBeforeAnyUpdateWhenCheckerRejectsDeletion() {
        ProductCategory root = category(1L, 0L, "食品", 1);
        givenDeleteData(root, List.of(), List.of(root));
        doThrow(new BizException(ProductErrorCode.CATEGORY_REPARENT_NAME_DUPLICATE))
                .when(deleteChecker).check(any());

        assertThrows(BizException.class, () -> categoryService.delete(command(1L, false)));

        verify(categoryMapper, never()).batchLogicDelete(any());
        verify(categoryMapper, never()).batchUpdateParentId(any(), anyLong());
        verify(categoryMapper, never()).batchDecreaseLevel(any());
    }

    @Test
    void shouldRejectMissingCategoryBeforeAnyOtherQuery() {
        when(categoryMapper.selectById(99L)).thenReturn(null);

        BizException exception = assertThrows(BizException.class, () -> categoryService.delete(command(99L, false)));

        assertEquals(ProductErrorCode.CATEGORY_NOT_FOUND.getCode(), exception.getCode());
        verify(categoryMapper, never()).selectByParentId(anyLong());
        verify(categoryMapper, never()).selectSubtree(anyLong());
    }

    private void givenDeleteData(ProductCategory category, List<ProductCategory> directChildren,
                                 List<ProductCategory> subtree) {
        when(categoryMapper.selectById(category.getId())).thenReturn(category);
        when(categoryMapper.selectByParentId(category.getId())).thenReturn(directChildren);
        when(categoryMapper.selectByParentId(category.getParentId())).thenReturn(List.of(category));
        when(categoryMapper.selectSubtree(category.getId())).thenReturn(subtree);
    }

    private DeleteCategoryCommand command(Long categoryId, boolean deleteChildren) {
        DeleteCategoryCommand command = new DeleteCategoryCommand();
        command.setCategoryId(categoryId);
        command.setDeleteChildren(deleteChildren);
        return command;
    }

    private ProductCategory category(Long id, Long parentId, String name, int level) {
        ProductCategory category = new ProductCategory();
        category.setId(id);
        category.setParentId(parentId);
        category.setName(name);
        category.setLevel(level);
        category.setDeleted(0L);
        return category;
    }
}
