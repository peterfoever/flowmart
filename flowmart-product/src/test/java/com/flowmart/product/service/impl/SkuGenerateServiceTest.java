package com.flowmart.product.service.impl;

import com.flowmart.common.exception.BizException;
import com.flowmart.common.exception.CommonErrorCode;
import com.flowmart.product.calculator.SpecHashCalculator;
import com.flowmart.product.convert.SkuConverter;
import com.flowmart.product.dto.SkuGenerateDTO;
import com.flowmart.product.dto.SpecDTO;
import com.flowmart.product.entity.ProductSku;
import com.flowmart.product.entity.ProductSpu;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.generator.SkuCodeGenerator;
import com.flowmart.product.mapper.ProductSkuMapper;
import com.flowmart.product.mapper.ProductSpuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用真实转换器/组合算法/哈希；Mock 不证明 MySQL 回滚或行锁效果。 */
class SkuGenerateServiceTest {
    private final ProductSpuMapper spus = mock(ProductSpuMapper.class);
    private final ProductSkuMapper skus = mock(ProductSkuMapper.class);
    private final SkuCodeGenerator codes = mock(SkuCodeGenerator.class);
    private final SkuGenerateService service = new SkuGenerateService(spus, skus, codes, Mappers.getMapper(SkuConverter.class));
    private final List<ProductSku> inserted = new ArrayList<>();
    private ProductSpu spu;
    private SkuGenerateDTO request;

    @BeforeEach
    void setup() {
        spu = new ProductSpu();
        spu.setId(1L); spu.setStatus(0); spu.setDeleted(0L);
        spu.setSpecs(List.of()); spu.setMainImageUrl(" main.png ");
        request = new SkuGenerateDTO(); request.setPrice(new BigDecimal("99.90"));
        when(spus.selectByIdForUpdate(1L)).thenReturn(spu);
        var sequence = new AtomicInteger();
        when(codes.next()).thenAnswer(call -> "SKU" + sequence.incrementAndGet());
        when(skus.batchInsert(anyList())).thenAnswer(call -> {
            List<ProductSku> batch = call.getArgument(0);
            inserted.addAll(batch);
            return batch.size();
        });
    }

    private SpecDTO spec(String name, int count) {
        var spec = new SpecDTO(); spec.setName(name);
        spec.setValues(IntStream.range(0, count).mapToObj(i -> "V" + i).toList());
        return spec;
    }

    private void noWrite() {
        verify(skus, never()).batchInsert(anyList());
        verifyNoInteractions(codes);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\u3000"})
    @DisplayName("省略或空白图片生成默认SKU，复制SPU主图且不修改入参")
    void defaultSku_fillsAllFields(String image) {
        request.setImageUrl(image);
        var ids = service.generate(1L, request, 42L);
        assertEquals(1, ids.size());
        var row = inserted.getFirst();
        assertEquals(row.getId(), ids.getFirst());
        assertNotNull(row.getId()); assertTrue(row.getId() > 0);
        assertEquals(1L, row.getSpuId()); assertEquals("SKU1", row.getSkuCode());
        assertEquals(request.getPrice(), row.getPrice());
        assertEquals("main.png", row.getImageUrl()); assertEquals(image, request.getImageUrl());
        assertEquals(List.of(), row.getSpecValues()); assertTrue(row.getIsDefault());
        assertEquals(SpecHashCalculator.calculate(List.of()), row.getSpecHash());
        assertEquals(0L, row.getDeleted()); assertEquals(0, row.getVersion());
        assertEquals(42L, row.getCreatedBy()); assertEquals(42L, row.getUpdatedBy());
        assertNotNull(row.getCreatedAt()); assertEquals(row.getCreatedAt(), row.getUpdatedAt());
        var order = inOrder(spus, skus);
        order.verify(spus).selectByIdForUpdate(1L);
        order.verify(skus).countActiveBySpuId(1L);
        order.verify(skus).batchInsert(anyList());
    }

    @Test
    @DisplayName("六种组合具有不同ID编码哈希，统一价格图片和审计时间")
    void sixCombinations_areCompleteAndIndependent() {
        spu.setSpecs(List.of(spec("颜色", 2), spec("尺寸", 3)));
        request.setImageUrl(" custom.png ");
        var ids = service.generate(1L, request, 0L);
        assertEquals(6, ids.size()); assertEquals(6, ids.stream().distinct().count());
        assertEquals(6, inserted.stream().map(ProductSku::getSkuCode).distinct().count());
        assertEquals(6, inserted.stream().map(ProductSku::getSpecHash).distinct().count());
        assertEquals(ids, inserted.stream().map(ProductSku::getId).toList());
        assertEquals(1, inserted.stream().map(ProductSku::getCreatedAt).distinct().count());
        assertTrue(inserted.stream().allMatch(s -> !s.getIsDefault() && s.getSpecValues().size() == 2
                && s.getImageUrl().equals("custom.png") && s.getPrice().equals(request.getPrice())));
        assertEquals(" custom.png ", request.getImageUrl());
    }

    @Test
    @DisplayName("210个有效组合按200和10分批，不逐条写入")
    void batchesRespectLimit() {
        spu.setSpecs(List.of(spec("A", 10), spec("B", 7), spec("C", 3)));
        assertEquals(210, service.generate(1L, request, 0L).size());
        var order = inOrder(skus);
        order.verify(skus).countActiveBySpuId(1L);
        order.verify(skus).batchInsert(argThat(rows -> rows.size() == 200));
        order.verify(skus).batchInsert(argThat(rows -> rows.size() == 10));
        verifyNoMoreInteractions(skus);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "deleted", "notDraft", "alreadyGenerated"})
    @DisplayName("SPU前置校验失败时不生成编码、不写入")
    void rejectedBeforeGeneration(String scenario) {
        ProductErrorCode expected;
        switch (scenario) {
            case "missing" -> { when(spus.selectByIdForUpdate(1L)).thenReturn(null); expected = ProductErrorCode.SPU_NOT_FOUND; }
            case "deleted" -> { spu.setDeleted(1L); expected = ProductErrorCode.SPU_NOT_FOUND; }
            case "notDraft" -> { spu.setStatus(1); expected = ProductErrorCode.SPU_NOT_DRAFT; }
            default -> { when(skus.countActiveBySpuId(1L)).thenReturn(1); expected = ProductErrorCode.SKU_ALREADY_GENERATED; }
        }
        assertEquals(expected.getCode(), assertThrows(BizException.class, () -> service.generate(1L, request, 0L)).getCode());
        noWrite();
    }

    @ParameterizedTest
    @ValueSource(strings = {"nullId", "zeroId", "nullDto", "nullActor", "negativeActor", "nullPrice", "negativePrice", "largePrice", "scale", "longImage"})
    @DisplayName("Service参数防御校验在访问数据库之前完成")
    void invalidArguments_failBeforeLocking(String scenario) {
        Long id = 1L, actor = 0L;
        switch (scenario) {
            case "nullId" -> id = null;
            case "zeroId" -> id = 0L;
            case "nullDto" -> request = null;
            case "nullActor" -> actor = null;
            case "negativeActor" -> actor = -1L;
            case "nullPrice" -> request.setPrice(null);
            case "negativePrice" -> request.setPrice(new BigDecimal("-0.01"));
            case "largePrice" -> request.setPrice(new BigDecimal("100000000"));
            case "scale" -> request.setPrice(new BigDecimal("0.001"));
            default -> request.setImageUrl("a".repeat(513));
        }
        Long finalId = id, finalActor = actor;
        assertEquals(CommonErrorCode.PARAM_INVALID.getCode(),
                assertThrows(BizException.class, () -> service.generate(finalId, request, finalActor)).getCode());
        verifyNoInteractions(spus, skus, codes);
    }

    @Test
    @DisplayName("组合超限时不写入")
    void tooManyCombinations_rejected() {
        spu.setSpecs(List.of(spec("A", 7), spec("B", 11), spec("C", 13)));
        assertEquals(ProductErrorCode.TOO_MANY_COMBINATIONS.getCode(),
                assertThrows(BizException.class, () -> service.generate(1L, request, 0L)).getCode());
        noWrite();
    }

    @Test
    @DisplayName("回退主图仍为空时拒绝写入")
    void missingFallbackImage_rejected() {
        spu.setMainImageUrl(null);
        assertEquals(CommonErrorCode.PARAM_INVALID.getCode(),
                assertThrows(BizException.class, () -> service.generate(1L, request, 0L)).getCode());
        noWrite();
    }

    @ParameterizedTest
    @ValueSource(strings = {"rows", "database", "duplicate"})
    @DisplayName("第二批失败向事务边界抛异常，不执行第三批；唯一冲突保留原因")
    void secondBatchFailure_stopsAndPropagates(String scenario) {
        spu.setSpecs(List.of(spec("A", 10), spec("B", 10), spec("C", 6)));
        var failure = scenario.equals("duplicate") ? new DuplicateKeyException("duplicate")
                : new DataAccessResourceFailureException("db failure");
        var calls = new AtomicInteger();
        when(skus.batchInsert(anyList())).thenAnswer(call -> {
            if (calls.incrementAndGet() == 2) {
                if (scenario.equals("rows")) return 199;
                throw failure;
            }
            return ((List<?>) call.getArgument(0)).size();
        });
        var error = assertThrows(RuntimeException.class, () -> service.generate(1L, request, 0L));
        if (scenario.equals("database")) assertSame(failure, error);
        else {
            assertInstanceOf(BizException.class, error);
            assertEquals((scenario.equals("rows") ? ProductErrorCode.SKU_GENERATE_FAILED : ProductErrorCode.SKU_DATA_CONFLICT).getCode(),
                    ((BizException) error).getCode());
            if (scenario.equals("duplicate")) assertSame(failure, error.getCause());
        }
        verify(skus, times(2)).batchInsert(anyList());
    }
}
