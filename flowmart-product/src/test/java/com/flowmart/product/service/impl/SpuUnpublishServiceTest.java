package com.flowmart.product.service.impl;

import com.flowmart.common.exception.BizException;
import com.flowmart.common.exception.CommonErrorCode;
import com.flowmart.common.exception.ErrorCode;
import com.flowmart.product.convert.SpuConverter;
import com.flowmart.product.entity.ProductSpu;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.generator.SpuCodeGenerator;
import com.flowmart.product.mapper.*;
import com.flowmart.product.service.SpuService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 下架业务与Spring事务代理回归；Mock不代表真实MySQL并发和回滚验证。 */
class SpuUnpublishServiceTest {
    private ProductSpuMapper spus;
    private ProductCategoryMapper categories;
    private ProductBrandMapper brands;
    private ProductSkuMapper skus;
    private SpuServiceImpl service;
    private ProductSpu spu;

    @BeforeEach
    void setUp() {
        spus = mock(ProductSpuMapper.class);
        categories = mock(ProductCategoryMapper.class);
        brands = mock(ProductBrandMapper.class);
        skus = mock(ProductSkuMapper.class);
        service = new SpuServiceImpl(mock(SpuConverter.class), spus, categories, brands,
                mock(SpuCodeGenerator.class), skus);
        spu = new ProductSpu();
        spu.setId(42L); spu.setDeleted(0L); spu.setStatus(1); spu.setVersion(7);
        // 故意不填名称、主图、规格等资料：它们不应成为下架前置条件。
        when(spus.selectByIdForUpdate(42L)).thenReturn(spu);
        when(spus.updateStatus(any(), any(), any(), any(), any(), any())).thenReturn(1);
    }

    @AfterEach
    void noAssociationQueries() {
        verifyNoInteractions(categories, brands, skus);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7, 127, 128, 1000})
    @DisplayName("正常下架先锁SPU，再以旧版本更新1到2，并填充审计字段")
    void unpublish_usesOldVersionAndAuditFields(int version) {
        spu.setVersion(version);
        var before = LocalDateTime.now();
        service.unpublish(42L, version);
        var after = LocalDateTime.now();
        var time = ArgumentCaptor.forClass(LocalDateTime.class);
        var order = inOrder(spus);
        order.verify(spus).selectByIdForUpdate(42L);
        order.verify(spus).updateStatus(eq(42L), eq(1), eq(2), eq(version), eq(0L), time.capture());
        verifyNoMoreInteractions(spus);
        assertFalse(time.getValue().isBefore(before));
        assertFalse(time.getValue().isAfter(after));
        assertEquals(version, spu.getVersion(), "版本递增交给SQL，不提前修改查询实体");
        assertEquals(1, spu.getStatus());
    }

    @Test
    @SuppressWarnings("removal")
    @DisplayName("回归：数值同为128但引用不同的Integer也必须允许下架")
    void equalVersionsWithDifferentReferences_succeed() {
        // 仅在回归测试中显式构造，避免JVM缓存配置改变而失去引用不等的测试条件。
        Integer storedVersion = new Integer(128);
        Integer requestVersion = new Integer(128);
        assertNotSame(storedVersion, requestVersion);
        assertEquals(storedVersion, requestVersion);
        spu.setVersion(storedVersion);
        service.unpublish(42L, requestVersion);
        verify(spus).updateStatus(eq(42L), eq(1), eq(2), eq(128), eq(0L), any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    @DisplayName("非法ID在数据库访问之前拒绝")
    void invalidId_rejected(Long id) {
        assertEquals(CommonErrorCode.PARAM_INVALID.getCode(),
                assertThrows(BizException.class, () -> service.unpublish(id, 7)).getCode());
        verifyNoInteractions(spus);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1})
    @DisplayName("直接调用Service时空版本与负版本也被拒绝")
    void invalidVersion_rejected(Integer version) {
        assertEquals(CommonErrorCode.PARAM_INVALID.getCode(),
                assertThrows(BizException.class, () -> service.unpublish(42L, version)).getCode());
        verifyNoInteractions(spus);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("不存在或已删除的SPU不能下架")
    void missingSpu_rejected(boolean deleted) {
        if (deleted) spu.setDeleted(42L);
        else when(spus.selectByIdForUpdate(42L)).thenReturn(null);
        rejected(ProductErrorCode.SPU_NOT_FOUND);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, 2, -1, 3})
    @DisplayName("草稿、重复下架及未知状态拒绝，且不执行更新")
    void invalidStatus_rejected(Integer status) {
        spu.setStatus(status);
        rejected(ProductErrorCode.SPU_NOT_ON_SHELF);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {6, 8, 128})
    @DisplayName("版本不匹配或存储版本异常时拒绝更新")
    void differentVersion_rejected(Integer storedVersion) {
        spu.setVersion(storedVersion);
        rejected(ProductErrorCode.SPU_VERSION_CONFLICT);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, -1})
    @DisplayName("更新影响行数不是1时不能返回成功")
    void unexpectedRowCount_rejected(int affected) {
        when(spus.updateStatus(any(), any(), any(), any(), any(), any())).thenReturn(affected);
        assertEquals(ProductErrorCode.SPU_UPDATE_FAILED.getCode(),
                assertThrows(BizException.class, () -> service.unpublish(42L, 7)).getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "stateFailure", "zeroRows", "databaseFailure"})
    @DisplayName("事务代理覆盖锁定和更新，成功提交、校验或数据库失败回滚")
    void transactionProxy_coversWholeOperation(String scenario) {
        var manager = mock(PlatformTransactionManager.class);
        var status = mock(TransactionStatus.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        var factory = new ProxyFactory(service);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        var transactional = (SpuService) factory.getProxy();
        switch (scenario) {
            case "stateFailure" -> {
                spu.setStatus(0);
                assertThrows(BizException.class, () -> transactional.unpublish(42L, 7));
            }
            case "zeroRows" -> {
                when(spus.updateStatus(any(), any(), any(), any(), any(), any())).thenReturn(0);
                assertThrows(BizException.class, () -> transactional.unpublish(42L, 7));
            }
            case "databaseFailure" -> {
                var failure = new DataAccessResourceFailureException("test failure");
                when(spus.updateStatus(any(), any(), any(), any(), any(), any())).thenThrow(failure);
                assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                        () -> transactional.unpublish(42L, 7)));
            }
            default -> transactional.unpublish(42L, 7);
        }
        var order = inOrder(manager, spus);
        order.verify(manager).getTransaction(any(TransactionDefinition.class));
        order.verify(spus).selectByIdForUpdate(42L);
        if (scenario.equals("stateFailure")) {
            verify(spus, never()).updateStatus(any(), any(), any(), any(), any(), any());
        } else {
            order.verify(spus).updateStatus(eq(42L), eq(1), eq(2), eq(7), eq(0L), any());
        }
        if (scenario.equals("success")) {
            order.verify(manager).commit(status);
            verify(manager, never()).rollback(any());
        } else {
            order.verify(manager).rollback(status);
            verify(manager, never()).commit(any());
        }
    }

    private void rejected(ErrorCode expected) {
        assertEquals(expected.getCode(), assertThrows(BizException.class, () -> service.unpublish(42L, 7)).getCode());
        verify(spus, never()).updateStatus(any(), any(), any(), any(), any(), any());
    }
}
