package com.flowmart.product.service.impl;

import com.flowmart.common.exception.BizException;
import com.flowmart.product.checker.CategoryNameConflictChecker;
import com.flowmart.product.context.CategoryDeleteContext;
import com.flowmart.product.entity.ProductCategory;
import com.flowmart.product.enums.CategoryStatus;
import com.flowmart.product.enums.ProductErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CategoryNameConflictChecker 单元测试
 */
class CategoryNameConflictCheckerTest {

    private CategoryNameConflictChecker checker;

    @BeforeEach
    void setUp() {
        checker = new CategoryNameConflictChecker();
    }

    // ============================================================
    // 测试场景1：级联删除 → 直接通过
    // ============================================================
    @Test
    void shouldPass_whenDeleteChildrenIsTrue() {
        // 准备：级联删除，即使有子类目也不检查名称冲突
        CategoryDeleteContext context = CategoryDeleteContext.builder()
                .category(createCategory(1L, 0L, "食品饮料", 1))
                .deleteChildren(true)
                .directChildren(Arrays.asList(
                        createCategory(2L, 1L, "休闲零食", 2),
                        createCategory(3L, 1L, "饮料", 2)
                ))
                .targetParentChildren(Collections.emptyList())
                .build();

        // 执行 + 验证：不抛异常
        assertDoesNotThrow(() -> checker.check(context));
    }

    // ============================================================
    // 测试场景2：无直接子类目 → 直接通过
    // ============================================================
    @Test
    void shouldPass_whenNoDirectChildren() {
        // 准备：无直接子类目
        CategoryDeleteContext context = CategoryDeleteContext.builder()
                .category(createCategory(1L, 0L, "食品饮料", 1))
                .deleteChildren(false)
                .directChildren(Collections.emptyList())
                .targetParentChildren(Arrays.asList(
                        createCategory(4L, 0L, "母婴用品", 1)
                ))
                .build();

        // 执行 + 验证：不抛异常
        assertDoesNotThrow(() -> checker.check(context));
    }

    // ============================================================
    // 测试场景3：目标父类下已有同名类目 → 抛异常
    // ============================================================
    @Test
    void shouldThrow_whenNameConflictWithTargetParent() {
        // 准备：删除类目 1（食品饮料），其子类目有"休闲零食"
        // 目标父类目（0）下已有"休闲零食"
        CategoryDeleteContext context = CategoryDeleteContext.builder()
                .category(createCategory(1L, 0L, "食品饮料", 1))
                .deleteChildren(false)
                .directChildren(Arrays.asList(
                        createCategory(2L, 1L, "休闲零食", 2)
                ))
                .targetParentChildren(Arrays.asList(
                        createCategory(4L, 0L, "母婴用品", 1),
                        createCategory(5L, 0L, "休闲零食", 1)  // 同名冲突
                ))
                .build();

        // 执行 + 验证：抛异常
        BizException exception = assertThrows(
                BizException.class,
                () -> checker.check(context)
        );

        assertEquals(ProductErrorCode.CATEGORY_REPARENT_NAME_DUPLICATE.getCode(),
                exception.getCode());
        assertTrue(exception.getMessage().contains("休闲零食"));
    }

    // ============================================================
    // 测试场景4：多个名称冲突 → 抛异常，消息包含所有冲突名
    // ============================================================
    @Test
    void shouldThrow_whenMultipleNameConflicts() {
        // 准备：删除类目 1，其子类目有"休闲零食"和"饮料"
        // 目标父类目下已有"休闲零食"和"饮料"
        CategoryDeleteContext context = CategoryDeleteContext.builder()
                .category(createCategory(1L, 0L, "食品饮料", 1))
                .deleteChildren(false)
                .directChildren(Arrays.asList(
                        createCategory(2L, 1L, "休闲零食", 2),
                        createCategory(3L, 1L, "饮料", 2)
                ))
                .targetParentChildren(Arrays.asList(
                        createCategory(4L, 0L, "母婴用品", 1),
                        createCategory(5L, 0L, "休闲零食", 1),  // 冲突
                        createCategory(6L, 0L, "饮料", 1)       // 冲突
                ))
                .build();

        // 执行 + 验证
        BizException exception = assertThrows(
                BizException.class,
                () -> checker.check(context)
        );

        assertEquals(ProductErrorCode.CATEGORY_REPARENT_NAME_DUPLICATE.getCode(),
                exception.getCode());
        assertTrue(exception.getMessage().contains("休闲零食"));
        assertTrue(exception.getMessage().contains("饮料"));
    }

    // ============================================================
    // 测试场景5：仅与待删除节点同名，但目标父类下无其他冲突 → 通过
    // ============================================================
    @Test
    void shouldPass_whenOnlySelfNameConflictButTargetParentHasNoConflict() {
        // 准备：删除类目 1（食品饮料），其子类目有"休闲零食"
        // 目标父类目下已有"食品饮料"（这是待删除节点本身，不应算冲突）
        // 但没有名为"休闲零食"的类目
        CategoryDeleteContext context = CategoryDeleteContext.builder()
                .category(createCategory(1L, 0L, "食品饮料", 1))
                .deleteChildren(false)
                .directChildren(Arrays.asList(
                        createCategory(2L, 1L, "休闲零食", 2)
                ))
                .targetParentChildren(Arrays.asList(
                        createCategory(1L, 0L, "食品饮料", 1)  // 仅待删除节点自身
                ))
                .build();

        // 执行 + 验证：不抛异常
        assertDoesNotThrow(() -> checker.check(context));
    }

    // ============================================================
    // 测试场景6：目标父类下已有同名，但同名的是已删除节点 → 通过
    // ============================================================
    @Test
    void shouldPass_whenConflictNameIsDeleted() {
        // 准备：目标父类目下有已删除的"休闲零食"（deleted != 0）
        // 但 parentDirectChildren 只查 deleted=0，所以不会包含它
        // 这个场景验证的是：Mapper 查询时已过滤 deleted=0，
        // Checker 只拿已过滤后的数据做比较
        CategoryDeleteContext context = CategoryDeleteContext.builder()
                .category(createCategory(1L, 0L, "食品饮料", 1))
                .deleteChildren(false)
                .directChildren(Arrays.asList(
                        createCategory(2L, 1L, "休闲零食", 2)
                ))
                .targetParentChildren(Collections.emptyList())  // 已删除的不在列表中
                .build();

        // 执行 + 验证：不抛异常
        assertDoesNotThrow(() -> checker.check(context));
    }

    // ============================================================
    // 测试场景7：一级类目删除，子类目上提到根目录 → 同样检查根目录冲突
    // ============================================================
    @Test
    void shouldCheckRootConflict_whenReparentToRoot() {
        // 准备：删除一级类目 1（食品饮料），其子类目有"休闲零食"
        // 根目录下已有"休闲零食"（另一个一级类目）
        CategoryDeleteContext context = CategoryDeleteContext.builder()
                .category(createCategory(1L, 0L, "食品饮料", 1))
                .deleteChildren(false)
                .directChildren(Arrays.asList(
                        createCategory(2L, 1L, "休闲零食", 2)
                ))
                .targetParentChildren(Arrays.asList(
                        createCategory(5L, 0L, "休闲零食", 1)  // 根目录下同名
                ))
                .build();

        // 执行 + 验证：抛异常（一级类目也检查）
        BizException exception = assertThrows(
                BizException.class,
                () -> checker.check(context)
        );

        assertEquals(ProductErrorCode.CATEGORY_REPARENT_NAME_DUPLICATE.getCode(),
                exception.getCode());
    }

    // ============================================================
    // 测试场景8：删除二级类目，子类目上提到一级 → 检查一级目录冲突
    // ============================================================
    @Test
    void shouldCheckGrandparentConflict_whenReparentToGrandparent() {
        // 准备：删除二级类目 2（休闲零食），其子类目有"坚果炒货"
        // 一级目录下已有"坚果炒货"
        CategoryDeleteContext context = CategoryDeleteContext.builder()
                .category(createCategory(2L, 1L, "休闲零食", 2))
                .deleteChildren(false)
                .directChildren(Arrays.asList(
                        createCategory(3L, 2L, "坚果炒货", 3)
                ))
                .targetParentChildren(Arrays.asList(
                        createCategory(6L, 1L, "坚果炒货", 2)  // 同一父类目下同名
                ))
                .build();

        // 执行 + 验证：抛异常
        BizException exception = assertThrows(
                BizException.class,
                () -> checker.check(context)
        );

        assertEquals(ProductErrorCode.CATEGORY_REPARENT_NAME_DUPLICATE.getCode(),
                exception.getCode());
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private ProductCategory createCategory(Long id, Long parentId, String name, Integer level) {
        ProductCategory category = new ProductCategory();
        category.setId(id);
        category.setParentId(parentId);
        category.setName(name);
        category.setLevel(level);
        category.setStatus(CategoryStatus.ENABLED.getCode());
        category.setDeleted(0L);
        return category;
    }
}
