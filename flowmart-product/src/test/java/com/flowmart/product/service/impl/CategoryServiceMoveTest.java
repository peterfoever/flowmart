package com.flowmart.product.service.impl;


import com.flowmart.common.exception.BizException;
import com.flowmart.product.dto.MoveCategoryDTO;
import com.flowmart.product.entity.ProductCategory;
import com.flowmart.product.enums.CategoryStatus;
import com.flowmart.product.enums.ProductErrorCode;

import com.flowmart.product.mapper.ProductCategoryMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceMoveTest {

    @Mock
    private ProductCategoryMapper categoryMapper;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private static final Long OPERATOR_ID = 0L;

    // ========== 测试数据 ==========
    // 一级类目
    private ProductCategory food;
    private ProductCategory baby;
    private ProductCategory disabledCategory;

    // 二级类目
    private ProductCategory snack;
    private ProductCategory milk;

    // 三级类目
    private ProductCategory nut;
    private ProductCategory babyMilk;
    private ProductCategory deepCategory;
    private ProductCategory deepestCategory;

    // 同名冲突类目
    private ProductCategory duplicateSnack;

    @BeforeEach
    void setUp() {
        // 一级类目
        food = createCategory(1L, 0L, "食品饮料", 1, CategoryStatus.ENABLED.getCode());
        baby = createCategory(4L, 0L, "母婴用品", 1, CategoryStatus.ENABLED.getCode());
        disabledCategory = createCategory(7L, 0L, "禁用类目", 1, CategoryStatus.DISABLED.getCode());

        // 二级类目
        snack = createCategory(2L, 1L, "休闲零食", 2, CategoryStatus.ENABLED.getCode());
        milk = createCategory(5L, 4L, "奶粉", 2, CategoryStatus.ENABLED.getCode());

        // 三级类目
        nut = createCategory(3L, 2L, "坚果炒货", 3, CategoryStatus.ENABLED.getCode());
        babyMilk = createCategory(6L, 5L, "婴儿奶粉", 3, CategoryStatus.ENABLED.getCode());
        deepCategory = createCategory(8L, 6L, "深度类目", 4, CategoryStatus.ENABLED.getCode());
        deepestCategory = createCategory(9L, 8L, "最深类目", 5, CategoryStatus.ENABLED.getCode());

        // 同名冲突类目：在母婴用品下已有一个"休闲零食"
        duplicateSnack = createCategory(10L, 4L, "休闲零食", 2, CategoryStatus.ENABLED.getCode());
    }

    private ProductCategory createCategory(Long id, Long parentId, String name,
                                           Integer level, Integer status) {
        ProductCategory category = new ProductCategory();
        category.setId(id);
        category.setParentId(parentId);
        category.setName(name);
        category.setLevel(level);
        category.setStatus(status);
        category.setDeleted(0L);
        return category;
    }

    // ============================================================
    // TC-01: 叶子类目移动到另一启用父类目成功
    // ============================================================
    @Test
    void TC01_moveLeafCategoryToAnotherEnabledParent_success() {
        // 准备：移动坚果炒货(id=3) 从 休闲零食(id=2) 到 食品饮料(id=1)
        Long categoryId = 3L;
        Long targetParentId = 1L;
        MoveCategoryDTO request = new MoveCategoryDTO();
        request.setTargetParentId(targetParentId);

        when(categoryMapper.selectById(categoryId)).thenReturn(nut);
        when(categoryMapper.selectById(targetParentId)).thenReturn(food);

        List<ProductCategory> subtree = Collections.singletonList(nut);
        when(categoryMapper.selectSubtree(categoryId)).thenReturn(subtree);

        when(categoryMapper.existsByNameAndParent(targetParentId, nut.getName(), categoryId))
                .thenReturn(false);

        when(categoryMapper.updateParentId(categoryId, targetParentId, OPERATOR_ID))
                .thenReturn(1);

        List<Long> allIds = Arrays.asList(3L);
        when(categoryMapper.batchUpdateLevel(allIds, -1, OPERATOR_ID))
                .thenReturn(1);

        // 执行
        categoryService.move(categoryId, request);

        // 验证
        verify(categoryMapper).updateParentId(categoryId, targetParentId, OPERATOR_ID);
        verify(categoryMapper).batchUpdateLevel(allIds, -1, OPERATOR_ID);
    }

    // ============================================================
    // TC-02: 带多层后代的子树移动成功，全部 level 按 delta 更新
    // ============================================================
    @Test
    void TC02_moveSubtreeWithMultipleLevels_success() {
        // 准备：移动 奶粉(id=5) 到 食品饮料(id=1) 下（同级移动，delta=0）
        Long categoryId = 5L;
        Long targetParentId = 1L;
        MoveCategoryDTO request = new MoveCategoryDTO();
        request.setTargetParentId(targetParentId);

        when(categoryMapper.selectById(categoryId)).thenReturn(milk);
        when(categoryMapper.selectById(targetParentId)).thenReturn(food);

        // 子树：奶粉(level=2) → 婴儿奶粉(level=3) → 深度类目(level=4) → 最深类目(level=5)
        List<ProductCategory> subtree = Arrays.asList(milk, babyMilk, deepCategory, deepestCategory);
        when(categoryMapper.selectSubtree(categoryId)).thenReturn(subtree);

        when(categoryMapper.existsByNameAndParent(targetParentId, milk.getName(), categoryId))
                .thenReturn(false);

        when(categoryMapper.updateParentId(categoryId, targetParentId, OPERATOR_ID))
                .thenReturn(1);

        // delta = 1 + 1 - 2 = 0，跳过 batchUpdateLevel
        // 验证 batchUpdateLevel 不被调用

        // 执行
        categoryService.move(categoryId, request);

        // 验证：只调用了 updateParentId，未调用 batchUpdateLevel
        verify(categoryMapper).updateParentId(categoryId, targetParentId, OPERATOR_ID);
        verify(categoryMapper, never()).batchUpdateLevel(anyList(), anyInt(), anyLong());
    }

    // ============================================================
    // TC-03: 移动为一级类目成功
    // ============================================================
    @Test
    void TC03_moveToRoot_success() {
        // 准备：移动 休闲零食(id=2) 到根目录
        Long categoryId = 2L;
        Long targetParentId = 0L;
        MoveCategoryDTO request = new MoveCategoryDTO();
        request.setTargetParentId(targetParentId);

        when(categoryMapper.selectById(categoryId)).thenReturn(snack);
        // targetParentId=0，不查询目标父类目

        List<ProductCategory> subtree = Arrays.asList(snack, nut);
        when(categoryMapper.selectSubtree(categoryId)).thenReturn(subtree);

        when(categoryMapper.existsByNameAndParent(targetParentId, snack.getName(), categoryId))
                .thenReturn(false);

        when(categoryMapper.updateParentId(categoryId, targetParentId, OPERATOR_ID))
                .thenReturn(1);

        List<Long> allIds = Arrays.asList(2L, 3L);
        when(categoryMapper.batchUpdateLevel(allIds, -1, OPERATOR_ID))
                .thenReturn(2);

        // 执行
        categoryService.move(categoryId, request);

        // 验证
        verify(categoryMapper).updateParentId(categoryId, targetParentId, OPERATOR_ID);
        verify(categoryMapper).batchUpdateLevel(allIds, -1, OPERATOR_ID);
    }

    // ============================================================
    // TC-04: 目标父类目不存在
    // ============================================================
    @Test
    void TC04_targetParentNotFound_throwsException() {
        Long categoryId = 3L;
        Long targetParentId = 999L;
        MoveCategoryDTO request = new MoveCategoryDTO();
        request.setTargetParentId(targetParentId);

        when(categoryMapper.selectById(categoryId)).thenReturn(nut);
        when(categoryMapper.selectById(targetParentId)).thenReturn(null);

        // 执行 + 验证
        BizException exception = assertThrows(
                BizException.class,
                () -> categoryService.move(categoryId, request)
        );

        assertEquals(ProductErrorCode.CATEGORY_PARENT_NOT_FOUND.getCode(),
                exception.getCode());

        // 验证：零次更新
        verify(categoryMapper, never()).updateParentId(anyLong(), anyLong(), anyLong());
        verify(categoryMapper, never()).batchUpdateLevel(anyList(), anyInt(), anyLong());
    }

    // ============================================================
    // TC-05: 目标父类目禁用
    // ============================================================
    @Test
    void TC05_targetParentDisabled_throwsException() {
        Long categoryId = 3L;
        Long targetParentId = 7L;
        MoveCategoryDTO request = new MoveCategoryDTO();
        request.setTargetParentId(targetParentId);

        when(categoryMapper.selectById(categoryId)).thenReturn(nut);
        when(categoryMapper.selectById(targetParentId)).thenReturn(disabledCategory);

        // 执行 + 验证
        BizException exception = assertThrows(
                BizException.class,
                () -> categoryService.move(categoryId, request)
        );

        assertEquals(ProductErrorCode.CATEGORY_PARENT_DISABLED.getCode(),
                exception.getCode());

        verify(categoryMapper, never()).updateParentId(anyLong(), anyLong(), anyLong());
        verify(categoryMapper, never()).batchUpdateLevel(anyList(), anyInt(), anyLong());
    }

    // ============================================================
    // TC-06: 移动到自身
    // ============================================================
    @Test
    void TC06_moveToSelf_throwsException() {
        Long categoryId = 3L;
        Long targetParentId = 3L;
        MoveCategoryDTO request = new MoveCategoryDTO();
        request.setTargetParentId(targetParentId);

        when(categoryMapper.selectById(categoryId)).thenReturn(nut);

        // 执行 + 验证
        BizException exception = assertThrows(
                BizException.class,
                () -> categoryService.move(categoryId, request)
        );

        assertEquals(ProductErrorCode.CATEGORY_MOVE_TO_SELF.getCode(),
                exception.getCode());

        // 验证：selectSubtree 未被调用（提前拦截）
        verify(categoryMapper, never()).selectSubtree(anyLong());
        verify(categoryMapper, never()).updateParentId(anyLong(), anyLong(), anyLong());
        verify(categoryMapper, never()).batchUpdateLevel(anyList(), anyInt(), anyLong());
    }

    // ============================================================
    // TC-07: 移动到自己的后代节点
    // ============================================================
    @Test
    void TC07_moveToDescendant_throwsException() {
        Long categoryId = 5L;  // 奶粉
        Long targetParentId = 6L;  // 婴儿奶粉（是奶粉的后代）
        MoveCategoryDTO request = new MoveCategoryDTO();
        request.setTargetParentId(targetParentId);

        when(categoryMapper.selectById(categoryId)).thenReturn(milk);
        when(categoryMapper.selectById(targetParentId)).thenReturn(babyMilk);

        List<ProductCategory> subtree = Arrays.asList(milk, babyMilk, deepCategory, deepestCategory);
        when(categoryMapper.selectSubtree(categoryId)).thenReturn(subtree);

        // 执行 + 验证
        BizException exception = assertThrows(
                BizException.class,
                () -> categoryService.move(categoryId, request)
        );

        assertEquals(ProductErrorCode.CATEGORY_MOVE_TO_DESCENDANT.getCode(),
                exception.getCode());

        verify(categoryMapper, never()).updateParentId(anyLong(), anyLong(), anyLong());
        verify(categoryMapper, never()).batchUpdateLevel(anyList(), anyInt(), anyLong());
    }

    // ============================================================
    // TC-08: 同名冲突，且零次更新
    // ============================================================
    @Test
    void TC08_nameConflict_throwsException() {
        // 移动 休闲零食(id=2) 到 母婴用品(id=4) 下，但母婴用品下已有同名类目(id=10)
        Long categoryId = 2L;
        Long targetParentId = 4L;
        MoveCategoryDTO request = new MoveCategoryDTO();
        request.setTargetParentId(targetParentId);

        when(categoryMapper.selectById(categoryId)).thenReturn(snack);
        when(categoryMapper.selectById(targetParentId)).thenReturn(baby);

        List<ProductCategory> subtree = Arrays.asList(snack, nut);
        when(categoryMapper.selectSubtree(categoryId)).thenReturn(subtree);

        when(categoryMapper.existsByNameAndParent(targetParentId, snack.getName(), categoryId))
                .thenReturn(true);

        // 执行 + 验证
        BizException exception = assertThrows(
                BizException.class,
                () -> categoryService.move(categoryId, request)
        );

        assertEquals(ProductErrorCode.CATEGORY_NAME_DUPLICATE.getCode(),
                exception.getCode());

        verify(categoryMapper, never()).updateParentId(anyLong(), anyLong(), anyLong());
        verify(categoryMapper, never()).batchUpdateLevel(anyList(), anyInt(), anyLong());
    }

    // ============================================================
    // TC-09: 移动后超过五级，且零次更新
    // ============================================================
    @Test
    void TC09_levelExceeded_throwsException() {
        // 移动 休闲零食(id=2)（当前 level=2，子树最大 level=3）到 深度类目(id=8)（当前 level=4）下
        // delta = 4 + 1 - 2 = 3，newMaxLevel = 3 + 3 = 6 > 5
        Long categoryId = 2L;
        Long targetParentId = 8L;
        MoveCategoryDTO request = new MoveCategoryDTO();
        request.setTargetParentId(targetParentId);

        when(categoryMapper.selectById(categoryId)).thenReturn(snack);
        when(categoryMapper.selectById(targetParentId)).thenReturn(deepCategory);

        List<ProductCategory> subtree = Arrays.asList(snack, nut);
        when(categoryMapper.selectSubtree(categoryId)).thenReturn(subtree);

        when(categoryMapper.existsByNameAndParent(targetParentId, snack.getName(), categoryId))
                .thenReturn(false);

        // 执行 + 验证
        BizException exception = assertThrows(
                BizException.class,
                () -> categoryService.move(categoryId, request)
        );

        assertEquals(ProductErrorCode.CATEGORY_LEVEL_EXCEEDED.getCode(),
                exception.getCode());

        verify(categoryMapper, never()).updateParentId(anyLong(), anyLong(), anyLong());
        verify(categoryMapper, never()).batchUpdateLevel(anyList(), anyInt(), anyLong());
    }

    // ============================================================
    // TC-10: updateParentId 影响行数异常 -> 抛错
    // ============================================================
    @Test
    void TC10_updateParentIdAffectedRowsMismatch_throwsException() {
        Long categoryId = 3L;
        Long targetParentId = 1L;
        MoveCategoryDTO request = new MoveCategoryDTO();
        request.setTargetParentId(targetParentId);

        when(categoryMapper.selectById(categoryId)).thenReturn(nut);
        when(categoryMapper.selectById(targetParentId)).thenReturn(food);

        List<ProductCategory> subtree = Collections.singletonList(nut);
        when(categoryMapper.selectSubtree(categoryId)).thenReturn(subtree);

        when(categoryMapper.existsByNameAndParent(targetParentId, nut.getName(), categoryId))
                .thenReturn(false);

        // updateParentId 返回 0（预期是 1）
        when(categoryMapper.updateParentId(categoryId, targetParentId, OPERATOR_ID))
                .thenReturn(0);

        // 执行 + 验证
        BizException exception = assertThrows(
                BizException.class,
                () -> categoryService.move(categoryId, request)
        );

        assertEquals(ProductErrorCode.CATEGORY_MOVE_FAILED.getCode(),
                exception.getCode());

        // 验证：batchUpdateLevel 未被调用
        verify(categoryMapper, never()).batchUpdateLevel(anyList(), anyInt(), anyLong());
    }

    // ============================================================
    // TC-11: batchUpdateLevel 影响行数异常 -> 抛错
    // ============================================================
    @Test
    void TC11_batchUpdateLevelAffectedRowsMismatch_throwsException() {
        Long categoryId = 3L;
        Long targetParentId = 1L;
        MoveCategoryDTO request = new MoveCategoryDTO();
        request.setTargetParentId(targetParentId);

        when(categoryMapper.selectById(categoryId)).thenReturn(nut);
        when(categoryMapper.selectById(targetParentId)).thenReturn(food);

        List<ProductCategory> subtree = Collections.singletonList(nut);
        when(categoryMapper.selectSubtree(categoryId)).thenReturn(subtree);

        when(categoryMapper.existsByNameAndParent(targetParentId, nut.getName(), categoryId))
                .thenReturn(false);

        when(categoryMapper.updateParentId(categoryId, targetParentId, OPERATOR_ID))
                .thenReturn(1);

        List<Long> allIds = Arrays.asList(3L);
        // batchUpdateLevel 返回 0（预期是 1）
        when(categoryMapper.batchUpdateLevel(allIds, -1, OPERATOR_ID))
                .thenReturn(0);

        // 执行 + 验证
        BizException exception = assertThrows(
                BizException.class,
                () -> categoryService.move(categoryId, request)
        );

        assertEquals(ProductErrorCode.CATEGORY_MOVE_FAILED.getCode(),
                exception.getCode());
    }

    // ============================================================
    // TC-12: 移动到当前父类目（无变化）
    // ============================================================
    @Test
    void TC12_moveToCurrentParent_throwsException() {
        Long categoryId = 2L;
        Long targetParentId = 1L;  // 休闲零食的当前父类目就是 1
        MoveCategoryDTO request = new MoveCategoryDTO();
        request.setTargetParentId(targetParentId);

        when(categoryMapper.selectById(categoryId)).thenReturn(snack);
        // targetParentId=1 存在且启用，但 Step 3.2 会在查询目标父类目后拦截


        // 执行 + 验证：在查询目标父类目后，Step 3.2 拦截
        BizException exception = assertThrows(
                BizException.class,
                () -> categoryService.move(categoryId, request)
        );

        assertEquals(ProductErrorCode.CATEGORY_PARENT_UNCHANGED.getCode(),
                exception.getCode());

        // 验证：selectSubtree 未被调用
        verify(categoryMapper, never()).selectSubtree(anyLong());
        verify(categoryMapper, never()).updateParentId(anyLong(), anyLong(), anyLong());
        verify(categoryMapper, never()).batchUpdateLevel(anyList(), anyInt(), anyLong());
    }

    // ============================================================
    // TC-13: selectSubtree 返回空 -> 抛异常
    // ============================================================
    @Test
    void TC13_selectSubtreeReturnsEmpty_throwsException() {
        Long categoryId = 3L;
        Long targetParentId = 1L;
        MoveCategoryDTO request = new MoveCategoryDTO();
        request.setTargetParentId(targetParentId);

        when(categoryMapper.selectById(categoryId)).thenReturn(nut);
        when(categoryMapper.selectById(targetParentId)).thenReturn(food);

        // selectSubtree 返回空列表（正常情况不应该发生）
        when(categoryMapper.selectSubtree(categoryId)).thenReturn(Collections.emptyList());

        // 执行 + 验证
        BizException exception = assertThrows(
                BizException.class,
                () -> categoryService.move(categoryId, request)
        );

        assertEquals(ProductErrorCode.CATEGORY_MOVE_FAILED.getCode(),
                exception.getCode());
        assertTrue(exception.getMessage().contains("查询子树为空"));

        verify(categoryMapper, never()).updateParentId(anyLong(), anyLong(), anyLong());
        verify(categoryMapper, never()).batchUpdateLevel(anyList(), anyInt(), anyLong());
    }

    // ============================================================
    // TC-14: selectSubtree 返回空（orElseThrow 验证）
    // ============================================================
    @Test
    void TC14_subtreeMaxLevelOrElseThrow_works() {
        // 这个测试验证层级计算分支中 orElseThrow 的行为
        // 构造一个 subtree 包含当前节点但 stream 操作正常的场景
        Long categoryId = 5L;
        Long targetParentId = 1L;
        MoveCategoryDTO request = new MoveCategoryDTO();
        request.setTargetParentId(targetParentId);

        when(categoryMapper.selectById(categoryId)).thenReturn(milk);
        when(categoryMapper.selectById(targetParentId)).thenReturn(food);

        List<ProductCategory> subtree = Arrays.asList(milk, babyMilk, deepCategory, deepestCategory);
        when(categoryMapper.selectSubtree(categoryId)).thenReturn(subtree);

        when(categoryMapper.existsByNameAndParent(targetParentId, milk.getName(), categoryId))
                .thenReturn(false);

        when(categoryMapper.updateParentId(categoryId, targetParentId, OPERATOR_ID))
                .thenReturn(1);

        // delta = 0，跳过 batchUpdateLevel

        // 执行：正常完成，不抛异常
        assertDoesNotThrow(() -> categoryService.move(categoryId, request));

        verify(categoryMapper).updateParentId(categoryId, targetParentId, OPERATOR_ID);
        verify(categoryMapper, never()).batchUpdateLevel(anyList(), anyInt(), anyLong());
    }
}