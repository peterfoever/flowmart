package com.flowmart.product.servisice.impl;

import com.flowmart.product.convert.CategoryConverter;
import com.flowmart.product.entity.ProductCategory;
import com.flowmart.product.enums.CategoryStatus;
import com.flowmart.product.mapper.ProductCategoryMapper;
import com.flowmart.product.service.impl.CategoryServiceImpl;
import com.flowmart.product.vo.CategoryTreeVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private ProductCategoryMapper categoryMapper;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private List<ProductCategory> mockCategories;

    @Mock
    private CategoryConverter categoryConverter;

    @BeforeEach
    void setUp() {
        mockCategories = Arrays.asList(
                // 食品饮料（启用，一级）
                createCategory(1L, 0L, "食品饮料", 1, 1, CategoryStatus.ENABLED.getCode()),
                // 休闲零食（启用，二级）
                createCategory(2L, 1L, "休闲零食", 2, 1, CategoryStatus.ENABLED.getCode()),
                // 坚果炒货（启用，三级）
                createCategory(3L, 2L, "坚果炒货", 3, 1, CategoryStatus.ENABLED.getCode()),
                // 母婴用品（禁用，一级）
                createCategory(4L, 0L, "母婴用品", 1, 2, CategoryStatus.DISABLED.getCode()),
                // 奶粉（启用，二级，父类目禁用）
                createCategory(5L, 4L, "奶粉", 2, 1, CategoryStatus.ENABLED.getCode())
        );
    }

    private ProductCategory createCategory(Long id, Long parentId, String name,
                                           Integer level, Integer sortNo, Integer status) {
        ProductCategory category = new ProductCategory();
        category.setId(id);
        category.setParentId(parentId);
        category.setName(name);
        category.setLevel(level);
        category.setSortNo(sortNo);
        category.setStatus(status);
        category.setDeleted(0L);
        return category;
    }

    // ========== 测试1：一级启用 → 二级启用 ==========
    @Test
    void testFrontTree_Level1Enabled_Level2Enabled() {
        when(categoryMapper.selectAllUndeletedOrdered()).thenReturn(mockCategories);

        List<CategoryTreeVO> tree = categoryService.listFrontTree();

        assertEquals(1, tree.size());  // 只有食品饮料

        CategoryTreeVO food = tree.get(0);
        assertEquals("食品饮料", food.getName());
        assertEquals(CategoryStatus.ENABLED.getCode(), food.getStatus());
        assertEquals(1, food.getChildren().size());

        CategoryTreeVO snack = food.getChildren().get(0);
        assertEquals("休闲零食", snack.getName());
        assertEquals(CategoryStatus.ENABLED.getCode(), snack.getStatus());
        assertEquals(1, snack.getChildren().size());

        CategoryTreeVO nut = snack.getChildren().get(0);
        assertEquals("坚果炒货", nut.getName());
        assertEquals(CategoryStatus.ENABLED.getCode(), nut.getStatus());
        assertTrue(nut.getChildren().isEmpty());
    }

    // ========== 测试2：一级禁用 → 二级启用 ==========
    @Test
    void testFrontTree_Level1Disabled_Level2Enabled() {
        when(categoryMapper.selectAllUndeletedOrdered()).thenReturn(mockCategories);

        List<CategoryTreeVO> tree = categoryService.listFrontTree();

        // 只有食品饮料，母婴用品整枝不返回
        assertEquals(1, tree.size());
        assertEquals("食品饮料", tree.get(0).getName());

        // 母婴用品不在树中
        boolean hasBaby = tree.stream().anyMatch(n -> "母婴用品".equals(n.getName()));
        assertFalse(hasBaby);

        // 奶粉不在树中
        boolean hasMilk = tree.stream().anyMatch(n -> "奶粉".equals(n.getName()));
        assertFalse(hasMilk);
    }

    @Test
    void testAdminTree_Level1Disabled_Level2Enabled_VisibleInFront() {
        when(categoryMapper.selectAllUndeletedOrdered()).thenReturn(mockCategories);

        List<CategoryTreeVO> tree = categoryService.listAdminTree();

        // 两棵都在
        assertEquals(2, tree.size());

        // 食品饮料（启用）
        CategoryTreeVO food = tree.stream()
                .filter(n -> "食品饮料".equals(n.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(food);
        assertTrue(food.getVisibleInFront());

        // 母婴用品（禁用）
        CategoryTreeVO baby = tree.stream()
                .filter(n -> "母婴用品".equals(n.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(baby);
        assertFalse(baby.getVisibleInFront());

        // 奶粉（二级，父类目禁用）
        CategoryTreeVO milk = tree.stream()
                .flatMap(n -> n.getChildren().stream())
                .filter(n -> "奶粉".equals(n.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(milk);
        assertFalse(milk.getVisibleInFront());  // ✅ 父类目禁用，所以 false
    }

    // ========== 测试3：一级启用 → 二级禁用 ==========
    @Test
    void testFrontTree_Level1Enabled_Level2Disabled() {
        // 修改数据：休闲零食禁用
        mockCategories = Arrays.asList(
                createCategory(1L, 0L, "食品饮料", 1, 1, CategoryStatus.ENABLED.getCode()),
                createCategory(2L, 1L, "休闲零食", 2, 1, CategoryStatus.DISABLED.getCode()),  // ❌ 禁用
                createCategory(3L, 2L, "坚果炒货", 3, 1, CategoryStatus.ENABLED.getCode()),
                createCategory(4L, 0L, "母婴用品", 1, 2, CategoryStatus.DISABLED.getCode()),
                createCategory(5L, 4L, "奶粉", 2, 1, CategoryStatus.ENABLED.getCode())
        );

        when(categoryMapper.selectAllUndeletedOrdered()).thenReturn(mockCategories);

        List<CategoryTreeVO> tree = categoryService.listFrontTree();

        assertEquals(1, tree.size());

        CategoryTreeVO food = tree.get(0);
        assertEquals("食品饮料", food.getName());

        // 一级启用，但二级禁用 → 前台只保留一级，不返回禁用节点及其子节点
        assertEquals(0, food.getChildren().size());  // ✅ 子节点为空
    }

    @Test
    void testAdminTree_Level1Enabled_Level2Disabled_VisibleInFront() {
        // 修改数据：休闲零食禁用
        mockCategories = Arrays.asList(
                createCategory(1L, 0L, "食品饮料", 1, 1, CategoryStatus.ENABLED.getCode()),
                createCategory(2L, 1L, "休闲零食", 2, 1, CategoryStatus.DISABLED.getCode()),  // ❌ 禁用
                createCategory(3L, 2L, "坚果炒货", 3, 1, CategoryStatus.ENABLED.getCode()),
                createCategory(4L, 0L, "母婴用品", 1, 2, CategoryStatus.DISABLED.getCode()),
                createCategory(5L, 4L, "奶粉", 2, 1, CategoryStatus.ENABLED.getCode())
        );

        when(categoryMapper.selectAllUndeletedOrdered()).thenReturn(mockCategories);

        List<CategoryTreeVO> tree = categoryService.listAdminTree();

        CategoryTreeVO food = tree.stream()
                .filter(n -> "食品饮料".equals(n.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(food);
        assertTrue(food.getVisibleInFront());

        CategoryTreeVO snack = food.getChildren().stream()
                .filter(n -> "休闲零食".equals(n.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(snack);
        assertFalse(snack.getVisibleInFront());  // ✅ 自身禁用，false
    }

    // ========== 测试4：排序 ==========
    @Test
    void testSorting_SortNoSame_OrderById() {
        // 同级：sortNo 相同，按 id 升序
        mockCategories = Arrays.asList(
                createCategory(3L, 0L, "C类目", 1, 1, CategoryStatus.ENABLED.getCode()),
                createCategory(1L, 0L, "A类目", 1, 1, CategoryStatus.ENABLED.getCode()),
                createCategory(2L, 0L, "B类目", 1, 1, CategoryStatus.ENABLED.getCode())
        );

        when(categoryMapper.selectAllUndeletedOrdered()).thenReturn(mockCategories);

        List<CategoryTreeVO> tree = categoryService.listFrontTree();

        assertEquals(3, tree.size());
        assertEquals("A类目", tree.get(0).getName());
        assertEquals("B类目", tree.get(1).getName());
        assertEquals("C类目", tree.get(2).getName());
    }

    // ========== 测试5：验证 SQL 只调用一次 ==========
    @Test
    void testMapperCalledOnlyOnce() {
        when(categoryMapper.selectAllUndeletedOrdered()).thenReturn(mockCategories);

        categoryService.listFrontTree();
        categoryService.listFrontTree();

        // ✅ 每次调用只查询一次
        verify(categoryMapper, times(2)).selectAllUndeletedOrdered();
    }

    @Test
    void testMapperCalledOnlyOnce_Admin() {
        when(categoryMapper.selectAllUndeletedOrdered()).thenReturn(mockCategories);

        categoryService.listAdminTree();
        categoryService.listAdminTree();

        verify(categoryMapper, times(2)).selectAllUndeletedOrdered();
    }
}
