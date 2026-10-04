package com.flowmart.product.service.impl;

import com.flowmart.common.exception.BizException;
import com.flowmart.common.exception.CommonErrorCode;
import com.flowmart.product.convert.SkuConverter;
import com.flowmart.product.dto.SkuUpdateDTO;
import com.flowmart.product.entity.ProductSku;
import com.flowmart.product.entity.ProductSpu;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.mapper.ProductSkuMapper;
import com.flowmart.product.mapper.ProductSpuMapper;
import com.flowmart.product.service.SkuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证业务分支与 Spring 事务代理；Mock 事务管理器不能证明 MySQL 回滚或并发互斥。 */
class SkuUpdateServiceTest {
    private ProductSkuMapper skus;
    private ProductSpuMapper spus;
    private SkuServiceImpl service;
    private ProductSku sku;
    private ProductSpu spu;
    private SkuUpdateDTO request;

    @BeforeEach
    void setUp() {
        skus = mock(ProductSkuMapper.class);
        spus = mock(ProductSpuMapper.class);
        service = new SkuServiceImpl(Mappers.getMapper(SkuConverter.class), skus, spus);
        sku = new ProductSku();
        sku.setId(100L); sku.setSpuId(42L); sku.setDeleted(0L); sku.setVersion(3);
        spu = new ProductSpu();
        spu.setId(42L); spu.setDeleted(0L); spu.setStatus(0); spu.setVersion(19);
        spu.setMainImageUrl("spu-main.png");
        request = new SkuUpdateDTO();
        request.setPrice(new BigDecimal("129.90")); request.setImageUrl("sku.png"); request.setVersion(3);
        when(skus.selectById(100L)).thenReturn(sku);
        when(spus.selectByIdForUpdate(42L)).thenReturn(spu);
        when(skus.updatePriceAndImage(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    }

    @Test
    @DisplayName("先定位SKU再锁定SPU，用客户端SKU版本更新并填充服务端审计字段")
    void update_usesRequestVersionAndCorrectLockOrder() {
        var before = LocalDateTime.now();
        service.updateSku(100L, request);
        var after = LocalDateTime.now();
        var time = ArgumentCaptor.forClass(LocalDateTime.class);
        var order = inOrder(skus, spus);
        order.verify(skus).selectById(100L);
        order.verify(spus).selectByIdForUpdate(42L);
        order.verify(skus).updatePriceAndImage(eq(100L), eq(42L), eq(3), eq(request.getPrice()),
                eq("sku.png"), eq(0L), time.capture());
        assertFalse(time.getValue().isBefore(before));
        assertFalse(time.getValue().isAfter(after));
        assertEquals(3, request.getVersion());
        verifyNoMoreInteractions(skus, spus);
    }

    @Test
    @DisplayName("图片去掉首尾空白后再持久化，不修改调用者DTO")
    void update_persistsTrimmedImageWithoutMutatingRequest() {
        request.setImageUrl(" \u3000sku.png\u3000 ");
        service.updateSku(100L, request);
        verify(skus).updatePriceAndImage(eq(100L), eq(42L), eq(3), eq(request.getPrice()),
                eq("sku.png"), eq(0L), any(LocalDateTime.class));
        assertEquals(" \u3000sku.png\u3000 ", request.getImageUrl());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "99999999.99"})
    @DisplayName("Service允许合法价格边界及512字符图片")
    void validBoundaries_arePersistedExactly(String price) {
        request.setPrice(new BigDecimal(price));
        request.setImageUrl("a".repeat(512));
        request.setVersion(0);
        service.updateSku(100L, request);
        verify(skus).updatePriceAndImage(eq(100L), eq(42L), eq(0), eq(new BigDecimal(price)),
                eq(request.getImageUrl()), eq(0L), any(LocalDateTime.class));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"-0.01", "100000000.00", "0.001", "1.230"})
    @DisplayName("Service直接调用也拒绝空价格、负数、溢出和超精度，不执行更新")
    void invalidPrice_isRejected(String price) {
        request.setPrice(price == null ? null : new BigDecimal(price));
        assertParamInvalid();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n", "\u3000"})
    @DisplayName("编辑Service拒绝空白图片，不沿用生成接口的SPU主图回退规则")
    void blankImage_isRejected(String image) {
        request.setImageUrl(image);
        assertParamInvalid();
    }

    @Test
    @DisplayName("Service拒绝超过512字符图片")
    void oversizedImage_isRejected() {
        request.setImageUrl("a".repeat(513));
        assertParamInvalid();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1})
    @DisplayName("Service拒绝缺失或负数版本，不将非法参数报告为版本冲突")
    void invalidVersion_isRejected(Integer version) {
        request.setVersion(version);
        assertParamInvalid();
    }

    @Test
    @DisplayName("空请求返回参数错误，不抛空指针异常")
    void nullRequest_isRejected() {
        request = null;
        assertParamInvalid();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    @DisplayName("Service拒绝非法SKU ID")
    void invalidId_isRejected(Long id) {
        var error = assertThrows(BizException.class, () -> service.updateSku(id, request));
        assertEquals(CommonErrorCode.PARAM_INVALID.getCode(), error.getCode());
        noUpdate();
    }

    @Test
    @DisplayName("有效SKU查询无结果时停止，不锁定SPU或更新")
    void missingSku_isRejected() {
        when(skus.selectById(100L)).thenReturn(null);
        assertBusinessError(ProductErrorCode.SKU_NOT_FOUND);
        verifyNoInteractions(spus);
        noUpdate();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "deleted", "onShelf", "offShelf"})
    @DisplayName("所属SPU不存在、已删除、上架或下架时不修改SKU")
    void invalidSpu_isRejected(String scenario) {
        var expected = ProductErrorCode.SPU_NOT_FOUND;
        switch (scenario) {
            case "missing" -> when(spus.selectByIdForUpdate(42L)).thenReturn(null);
            case "deleted" -> spu.setDeleted(42L);
            case "onShelf" -> { spu.setStatus(1); expected = ProductErrorCode.SPU_NOT_DRAFT; }
            case "offShelf" -> { spu.setStatus(2); expected = ProductErrorCode.SPU_NOT_DRAFT; }
        }
        assertBusinessError(expected);
        noUpdate();
    }

    @Test
    @DisplayName("查询得到新版本也不能覆盖请求的旧版本，UPDATE零行返回SKU版本冲突")
    void staleVersion_isNeverReplacedWithDatabaseVersion() {
        sku.setVersion(4);
        when(skus.updatePriceAndImage(eq(100L), eq(42L), eq(3), any(), any(), any(), any())).thenReturn(0);
        assertBusinessError(ProductErrorCode.SKU_VERSION_CONFLICT);
        verify(skus).updatePriceAndImage(eq(100L), eq(42L), eq(3), eq(request.getPrice()),
                eq("sku.png"), eq(0L), any(LocalDateTime.class));
        assertEquals(3, request.getVersion());
    }

    @Test
    @DisplayName("初次读取版本相同但UPDATE零行，也必须报告冲突而非成功")
    void raceAfterRead_returnsConflict() {
        when(skus.updatePriceAndImage(any(), any(), any(), any(), any(), any(), any())).thenReturn(0);
        assertBusinessError(ProductErrorCode.SKU_VERSION_CONFLICT);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("更新零行后重查返回空或归属不匹配时，按当前分支报告SKU不存在")
    void zeroRows_recheckMissingOrDifferentOwner(boolean differentOwner) {
        // 仅覆盖当前业务分支，不证明可重复读事务的普通查询能读到并发删除。
        ProductSku reread = null;
        if (differentOwner) {
            reread = new ProductSku();
            reread.setId(100L);
            reread.setSpuId(99L);
        }
        when(skus.selectById(100L)).thenReturn(sku, reread);
        when(skus.updatePriceAndImage(any(), any(), any(), any(), any(), any(), any())).thenReturn(0);
        assertBusinessError(ProductErrorCode.SKU_NOT_FOUND);
        verify(skus, times(2)).selectById(100L);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 2})
    @DisplayName("异常更新行数返回SKU_UPDATE_FAILED")
    void unexpectedRowCount_isRejected(int rows) {
        when(skus.updatePriceAndImage(any(), any(), any(), any(), any(), any(), any())).thenReturn(rows);
        assertBusinessError(ProductErrorCode.SKU_UPDATE_FAILED);
    }

    @Test
    @DisplayName("数据库故障原样向外传播，不吞异常或伪装成版本冲突")
    void databaseFailure_isPropagated() {
        var failure = new DataAccessResourceFailureException("test connection failure");
        when(skus.updatePriceAndImage(any(), any(), any(), any(), any(), any(), any())).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class, () -> service.updateSku(100L, request)));
        verify(skus, times(1)).selectById(100L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("真实Spring事务代理在查询前开启事务，成功提交、业务异常回滚")
    void transactionProxy_wrapsLockAndWrite(boolean conflict) {
        var manager = mock(PlatformTransactionManager.class);
        var status = mock(TransactionStatus.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        var proxy = new ProxyFactory(service);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        var transactional = (SkuService) proxy.getProxy();
        if (conflict) {
            when(skus.updatePriceAndImage(any(), any(), any(), any(), any(), any(), any())).thenReturn(0);
            assertEquals(ProductErrorCode.SKU_VERSION_CONFLICT.getCode(),
                    assertThrows(BizException.class, () -> transactional.updateSku(100L, request)).getCode());
        } else {
            transactional.updateSku(100L, request);
        }
        var order = inOrder(manager, skus, spus);
        order.verify(manager).getTransaction(any(TransactionDefinition.class));
        order.verify(skus).selectById(100L);
        order.verify(spus).selectByIdForUpdate(42L);
        order.verify(skus).updatePriceAndImage(eq(100L), eq(42L), eq(3), any(), any(), eq(0L), any());
        if (conflict) {
            order.verify(manager).rollback(status);
            verify(manager, never()).commit(any());
        } else {
            order.verify(manager).commit(status);
            verify(manager, never()).rollback(any());
        }
    }

    private void assertParamInvalid() {
        var error = assertThrows(BizException.class, () -> service.updateSku(100L, request));
        assertEquals(CommonErrorCode.PARAM_INVALID.getCode(), error.getCode());
        noUpdate();
    }

    private void assertBusinessError(ProductErrorCode expected) {
        var error = assertThrows(BizException.class, () -> service.updateSku(100L, request));
        assertEquals(expected.getCode(), error.getCode());
    }

    private void noUpdate() {
        verify(skus, never()).updatePriceAndImage(any(), any(), any(), any(), any(), any(), any());
    }
}
