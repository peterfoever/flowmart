package com.flowmart.product.service.impl;

import com.flowmart.common.exception.BizException;
import com.flowmart.common.exception.CommonErrorCode;
import com.flowmart.common.exception.ErrorCode;
import com.flowmart.product.calculator.SpecHashCalculator;
import com.flowmart.product.convert.SpuConverter;
import com.flowmart.product.dto.SpecDTO;
import com.flowmart.product.dto.SkuSpecValueDTO;
import com.flowmart.product.entity.*;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.generator.SpuCodeGenerator;
import com.flowmart.product.generator.SpecCombinationGenerator;
import com.flowmart.product.mapper.*;
import com.flowmart.product.service.SpuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 业务与事务代理回归；Mock 事务管理器不证明 MySQL 实际回滚或并发互斥。 */
class SpuPublishServiceTest {
    private ProductSpuMapper spus;
    private ProductCategoryMapper categories;
    private ProductBrandMapper brands;
    private ProductSkuMapper skus;
    private SpuServiceImpl service;
    private ProductSpu spu;
    private ProductCategory category;
    private ProductBrand brand;
    private ProductSku sku;

    @BeforeEach
    void setUp() {
        spus = mock(ProductSpuMapper.class);
        categories = mock(ProductCategoryMapper.class);
        brands = mock(ProductBrandMapper.class);
        skus = mock(ProductSkuMapper.class);
        service = new SpuServiceImpl(mock(SpuConverter.class), spus, categories, brands,
                mock(SpuCodeGenerator.class), skus);
        spu = new ProductSpu();
        spu.setId(42L); spu.setDeleted(0L); spu.setStatus(0); spu.setVersion(7);
        spu.setName("测试商品"); spu.setMainImageUrl("main.png"); spu.setCategoryId(10L);
        spu.setSpecs(List.of());
        category = new ProductCategory();
        category.setId(10L); category.setDeleted(0L); category.setStatus(1);
        brand = new ProductBrand();
        brand.setId(20L); brand.setDeleted(0L); brand.setStatus(1);
        sku = sku(100L, List.of(), true);
        when(spus.selectByIdForUpdate(42L)).thenReturn(spu);
        when(categories.selectByIdForUpdate(10L)).thenReturn(category);
        when(categories.isLeafCategory(10L)).thenReturn(true);
        when(brands.selectByIdForUpdate(20L)).thenReturn(brand);
        when(categories.existsByCategoryIdAndBrandId(10L, 20L)).thenReturn(true);
        when(skus.selectActiveBySpuId(42L)).thenReturn(List.of(sku));
        when(spus.updateStatus(any(), any(), any(), any(), any(), any())).thenReturn(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    @DisplayName("无品牌无规格商品可从草稿或下架上架，必须执行更新并传旧版本")
    void defaultProduct_publishesWithOldVersion(int oldStatus) {
        spu.setStatus(oldStatus);
        service.publish(42L, 7);
        var order = inOrder(spus, categories, skus);
        order.verify(spus).selectByIdForUpdate(42L);
        order.verify(categories).selectByIdForUpdate(10L);
        order.verify(categories).isLeafCategory(10L);
        order.verify(skus).selectActiveBySpuId(42L);
        order.verify(spus).updateStatus(eq(42L), eq(oldStatus), eq(1), eq(7), eq(0L), any(LocalDateTime.class));
        verifyNoInteractions(brands);
        verify(categories, never()).existsByCategoryIdAndBrandId(any(), any());
        assertEquals(7, spu.getVersion(), "不在内存里预先递增版本");
    }

    @Test
    @DisplayName("品牌存在启用且绑定时按SPU、类目、品牌顺序加锁")
    void boundBrand_usesConsistentLockOrder() {
        spu.setBrandId(20L);
        service.publish(42L, 7);
        var order = inOrder(spus, categories, brands, skus);
        order.verify(spus).selectByIdForUpdate(42L);
        order.verify(categories).selectByIdForUpdate(10L);
        order.verify(brands).selectByIdForUpdate(20L);
        order.verify(categories).existsByCategoryIdAndBrandId(10L, 20L);
        order.verify(skus).selectActiveBySpuId(42L);
        order.verify(spus).updateStatus(eq(42L), eq(0), eq(1), eq(7), eq(0L), any());
    }

    @Test
    @DisplayName("完整多规格组合可上架，SKU返回顺序不影响结果")
    void completeCombinations_ignoreRowOrder() {
        withColorSpecs();
        when(skus.selectActiveBySpuId(42L)).thenReturn(List.of(
                sku(102L, List.of(value("颜色", "蓝")), false), sku));
        service.publish(42L, 7);
        verify(spus).updateStatus(eq(42L), eq(0), eq(1), eq(7), eq(0L), any());
    }

    @Test
    @DisplayName("恰好1000个完整规格组合允许上架")
    void maximumCombinations_accepted() {
        List<SpecDTO> definitions = new ArrayList<>();
        for (String name : List.of("颜色", "尺寸", "材质")) {
            var definition = new SpecDTO(); definition.setName(name);
            definition.setValues(java.util.stream.IntStream.range(0, 10).mapToObj(String::valueOf).toList());
            definitions.add(definition);
        }
        spu.setSpecs(definitions);
        var combinations = SpecCombinationGenerator.generate(definitions);
        List<ProductSku> rows = new ArrayList<>();
        for (int i = 0; i < combinations.size(); i++) {
            rows.add(sku(100L + i, combinations.get(i), false));
        }
        assertEquals(1000, rows.size());
        when(skus.selectActiveBySpuId(42L)).thenReturn(rows);
        service.publish(42L, 7);
        verify(spus).updateStatus(eq(42L), eq(0), eq(1), eq(7), eq(0L), any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    @DisplayName("非法ID在访问数据库前拒绝")
    void invalidId_rejected(Long id) {
        assertEquals(CommonErrorCode.PARAM_INVALID.getCode(),
                assertThrows(BizException.class, () -> service.publish(id, 7)).getCode());
        verifyNoInteractions(spus, categories, brands, skus);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1})
    @DisplayName("直接调用Service也拒绝空或负版本")
    void invalidVersion_rejected(Integer version) {
        assertEquals(CommonErrorCode.PARAM_INVALID.getCode(),
                assertThrows(BizException.class, () -> service.publish(42L, version)).getCode());
        verifyNoInteractions(spus, categories, brands, skus);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("SPU不存在或已删除拒绝上架")
    void missingSpu_rejected(boolean deleted) {
        if (deleted) spu.setDeleted(42L);
        else when(spus.selectByIdForUpdate(42L)).thenReturn(null);
        rejected(ProductErrorCode.SPU_NOT_FOUND);
        verifyNoInteractions(categories, brands, skus);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {1, -1, 3})
    @DisplayName("重复上架和非法状态拒绝且不查询关联资料")
    void invalidState_rejected(Integer state) {
        spu.setStatus(state);
        rejected(ProductErrorCode.SPU_INVALID_STATE);
        verifyNoInteractions(categories, brands, skus);
    }

    @Test
    @DisplayName("过期版本拒绝且不查询关联资料")
    void staleVersion_rejected() {
        spu.setVersion(8);
        rejected(ProductErrorCode.SPU_VERSION_CONFLICT);
        verifyNoInteractions(categories, brands, skus);
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "mainImage", "categoryId"})
    @DisplayName("商品关键资料缺失不允许上架")
    void missingDetails_rejected(String field) {
        switch (field) {
            case "name" -> spu.setName(" ");
            case "mainImage" -> spu.setMainImageUrl(null);
            case "categoryId" -> spu.setCategoryId(null);
        }
        rejected(CommonErrorCode.PARAM_INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "deleted", "disabled", "nonLeaf"})
    @DisplayName("类目不存在、删除、禁用、非叶子返回对应错误")
    void invalidCategory_rejected(String scenario) {
        ErrorCode expected = ProductErrorCode.CATEGORY_NOT_FOUND;
        switch (scenario) {
            case "missing" -> when(categories.selectByIdForUpdate(10L)).thenReturn(null);
            case "deleted" -> category.setDeleted(10L);
            case "disabled" -> { category.setStatus(0); expected = ProductErrorCode.CATEGORY_DISABLED; }
            case "nonLeaf" -> {
                when(categories.isLeafCategory(10L)).thenReturn(false);
                expected = ProductErrorCode.CATEGORY_NOT_LEAF;
            }
        }
        rejected(expected);
        verifyNoInteractions(skus);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "deleted", "disabled", "unbound"})
    @DisplayName("品牌不存在、删除、禁用、未绑定返回对应错误")
    void invalidBrand_rejected(String scenario) {
        spu.setBrandId(20L);
        ErrorCode expected = ProductErrorCode.BRAND_NOT_FOUND;
        switch (scenario) {
            case "missing" -> when(brands.selectByIdForUpdate(20L)).thenReturn(null);
            case "deleted" -> brand.setDeleted(20L);
            case "disabled" -> { brand.setStatus(0); expected = ProductErrorCode.BRAND_DISABLED; }
            case "unbound" -> {
                when(categories.existsByCategoryIdAndBrandId(10L, 20L)).thenReturn(false);
                expected = ProductErrorCode.CATEGORY_BRAND_NOT_BOUND;
            }
        }
        rejected(expected);
        verifyNoInteractions(skus);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1001})
    @DisplayName("无SKU或超过1000条拒绝上架")
    void invalidSkuCount_rejected(int count) {
        when(skus.selectActiveBySpuId(42L)).thenReturn(java.util.Collections.nCopies(count, sku));
        rejected(count == 0 ? ProductErrorCode.SKU_NOT_FOUND : ProductErrorCode.TOO_MANY_COMBINATIONS);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"-0.01", "100000000.00", "0.001"})
    @DisplayName("SKU价格为空、负数、超上限、超精度拒绝")
    void invalidPrice_rejected(String price) {
        sku.setPrice(price == null ? null : new BigDecimal(price));
        rejected(ProductErrorCode.SKU_PRICE_INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "99999999.99"})
    @DisplayName("SKU价格合法边界允许上架")
    void validPrice_accepted(String price) {
        sku.setPrice(new BigDecimal(price));
        service.publish(42L, 7);
        verify(spus).updateStatus(eq(42L), eq(0), eq(1), eq(7), eq(0L), any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\u3000"})
    @DisplayName("SKU图片为空白拒绝上架")
    void blankImage_rejected(String image) {
        sku.setImageUrl(image);
        rejected(ProductErrorCode.SKU_IMAGE_REQUIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nullDefinition", "nullValues", "notDefault", "nullFlag", "badHash", "extraSku", "extraValue"})
    @DisplayName("无规格商品必须是一个合法默认SKU，不能用null掩盖映射失败")
    void invalidDefaultSku_rejected(String scenario) {
        switch (scenario) {
            case "nullDefinition" -> spu.setSpecs(null);
            case "nullValues" -> sku.setSpecValues(null);
            case "notDefault" -> sku.setIsDefault(false);
            case "nullFlag" -> sku.setIsDefault(null);
            case "badHash" -> sku.setSpecHash("bad");
            case "extraSku" -> when(skus.selectActiveBySpuId(42L)).thenReturn(List.of(sku, sku));
            case "extraValue" -> {
                sku.setSpecValues(List.of(value("颜色", "红")));
                sku.setSpecHash(SpecHashCalculator.calculate(sku.getSpecValues()));
            }
        }
        rejected(ProductErrorCode.SKU_SPEC_MISMATCH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "unexpected", "duplicate", "tamperedValues", "wrongFlag", "blankValue"})
    @DisplayName("有规格商品拒绝缺组合、错组合、重复、哈希与内容不一致及错误默认标记")
    void invalidCombinations_rejected(String scenario) {
        withColorSpecs();
        var blue = sku(102L, List.of(value("颜色", "蓝")), false);
        var rows = new ArrayList<>(List.of(sku, blue));
        switch (scenario) {
            case "missing" -> rows.removeLast();
            case "unexpected" -> rows.set(1, sku(103L, List.of(value("颜色", "绿")), false));
            case "duplicate" -> rows.add(sku);
            case "tamperedValues" -> blue.setSpecValues(List.of(value("颜色", "绿")));
            case "wrongFlag" -> blue.setIsDefault(true);
            case "blankValue" -> blue.setSpecValues(List.of(value("颜色", " ")));
        }
        when(skus.selectActiveBySpuId(42L)).thenReturn(rows);
        rejected(ProductErrorCode.SKU_SPEC_MISMATCH);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    @DisplayName("影响行数不是1时拒绝伪成功")
    void affectedRowsMismatch_throws(int rows) {
        when(spus.updateStatus(any(), any(), any(), any(), any(), any())).thenReturn(rows);
        assertEquals(ProductErrorCode.SPU_UPDATE_FAILED.getCode(),
                assertThrows(BizException.class, () -> service.publish(42L, 7)).getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "zeroRows", "databaseFailure"})
    @DisplayName("真实Spring代理在锁定前开启事务，成功提交，更新失败或数据库异常回滚")
    void transactionProxy_wrapsWholeOperation(String scenario) {
        var manager = mock(PlatformTransactionManager.class);
        var status = mock(TransactionStatus.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        var factory = new ProxyFactory(service);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        var transactional = (SpuService) factory.getProxy();
        if (scenario.equals("zeroRows")) {
            when(spus.updateStatus(any(), any(), any(), any(), any(), any())).thenReturn(0);
            assertThrows(BizException.class, () -> transactional.publish(42L, 7));
        } else if (scenario.equals("databaseFailure")) {
            var failure = new DataAccessResourceFailureException("test failure");
            when(spus.updateStatus(any(), any(), any(), any(), any(), any())).thenThrow(failure);
            assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                    () -> transactional.publish(42L, 7)));
        } else {
            transactional.publish(42L, 7);
        }
        var order = inOrder(manager, spus);
        order.verify(manager).getTransaction(any(TransactionDefinition.class));
        order.verify(spus).selectByIdForUpdate(42L);
        order.verify(spus).updateStatus(eq(42L), eq(0), eq(1), eq(7), eq(0L), any());
        if (scenario.equals("success")) {
            order.verify(manager).commit(status);
            verify(manager, never()).rollback(any());
        } else {
            order.verify(manager).rollback(status);
            verify(manager, never()).commit(any());
        }
    }

    private void rejected(ErrorCode expected) {
        assertEquals(expected.getCode(), assertThrows(BizException.class, () -> service.publish(42L, 7)).getCode());
        verify(spus, never()).updateStatus(any(), any(), any(), any(), any(), any());
    }

    private void withColorSpecs() {
        SpecDTO color = new SpecDTO(); color.setName("颜色"); color.setValues(List.of("红", "蓝"));
        spu.setSpecs(List.of(color));
        sku.setSpecValues(List.of(value("颜色", "红")));
        sku.setSpecHash(SpecHashCalculator.calculate(sku.getSpecValues()));
        sku.setIsDefault(false);
    }

    private static ProductSku sku(Long id, List<SkuSpecValueDTO> values, boolean defaultSku) {
        var result = new ProductSku();
        result.setId(id); result.setSpuId(42L); result.setDeleted(0L);
        result.setSpecValues(values); result.setSpecHash(SpecHashCalculator.calculate(values));
        result.setIsDefault(defaultSku); result.setPrice(new BigDecimal("19.90")); result.setImageUrl("sku.png");
        return result;
    }

    private static SkuSpecValueDTO value(String name, String value) {
        var dto = new SkuSpecValueDTO(); dto.setName(name); dto.setValue(value); return dto;
    }
}
