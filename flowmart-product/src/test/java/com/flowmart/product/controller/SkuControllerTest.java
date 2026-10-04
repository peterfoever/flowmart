package com.flowmart.product.controller;

import com.flowmart.common.exception.BizException;
import com.flowmart.common.web.GlobalExceptionHandler;
import com.flowmart.product.dto.SkuGenerateDTO;
import com.flowmart.product.dto.SkuUpdateDTO;
import com.flowmart.product.dto.SkuSpecValueDTO;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.service.SkuService;
import com.flowmart.product.service.impl.SkuGenerateService;
import com.flowmart.product.vo.SkuListVO;
import com.flowmart.product.vo.SkuDetailVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 经 DispatcherServlet 验证真实路由、JSON、校验与异常处理；不启动数据库。 */
class SkuControllerTest {
    private static final String LIST = "/api/product/admin/spus/{spuId}/skus";
    private static final String GENERATE = LIST + "/generate";
    private static final String DETAIL = "/api/product/admin/skus/{id}";
    private static final String UPDATE_BODY = "{\"price\":129.90,\"imageUrl\":\"sku.png\",\"version\":3}";
    private SkuGenerateService generateService;
    private SkuService skuService;
    private LocalValidatorFactoryBean validator;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        generateService = mock(SkuGenerateService.class);
        skuService = mock(SkuService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        // standaloneSetup 不自动注册 @Validated 的 AOP，显式装配以真实验证路径参数。
        var proxy = new ProxyFactory(new SkuController(generateService, skuService));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new MethodValidationInterceptor(validator.getValidator()));
        mvc = MockMvcBuilders.standaloneSetup(proxy.getProxy())
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    @DisplayName("PUT修改路由绑定SKU ID和三个编辑字段，返回成功而非详情对象")
    void update_bindsPathAndBody() throws Exception {
        mvc.perform(put(DETAIL, 100).contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
        var dto = ArgumentCaptor.forClass(SkuUpdateDTO.class);
        verify(skuService).updateSku(eq(100L), dto.capture());
        assertEquals(new BigDecimal("129.90"), dto.getValue().getPrice());
        assertEquals("sku.png", dto.getValue().getImageUrl());
        assertEquals(3, dto.getValue().getVersion());
        verifyNoMoreInteractions(skuService);
        verifyNoInteractions(generateService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "99999999.99"})
    @DisplayName("修改接口接受价格上下界、版本0和512字符图片")
    void update_acceptsBoundaries(String price) throws Exception {
        String image = "a".repeat(512);
        String body = "{\"price\":" + price + ",\"imageUrl\":\"" + image + "\",\"version\":0}";
        mvc.perform(put(DETAIL, 100).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        var dto = ArgumentCaptor.forClass(SkuUpdateDTO.class);
        verify(skuService).updateSku(eq(100L), dto.capture());
        assertEquals(new BigDecimal(price), dto.getValue().getPrice());
        assertEquals(image, dto.getValue().getImageUrl());
        assertEquals(0, dto.getValue().getVersion());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"imageUrl\":\"a.png\",\"version\":3}",
            "{\"price\":null,\"imageUrl\":\"a.png\",\"version\":3}",
            "{\"price\":-0.01,\"imageUrl\":\"a.png\",\"version\":3}",
            "{\"price\":100000000.00,\"imageUrl\":\"a.png\",\"version\":3}",
            "{\"price\":1.001,\"imageUrl\":\"a.png\",\"version\":3}",
            "{\"price\":1.00,\"version\":3}",
            "{\"price\":1.00,\"imageUrl\":null,\"version\":3}",
            "{\"price\":1.00,\"imageUrl\":\"\",\"version\":3}",
            "{\"price\":1.00,\"imageUrl\":\"   \",\"version\":3}",
            "{\"price\":1.00,\"imageUrl\":\"a.png\"}",
            "{\"price\":1.00,\"imageUrl\":\"a.png\",\"version\":null}",
            "{\"price\":1.00,\"imageUrl\":\"a.png\",\"version\":-1}",
            "{broken", "null", ""})
    @DisplayName("修改请求拒绝非法价格、图片、版本和JSON，校验失败不进入Service")
    void update_rejectsInvalidBody(String body) throws Exception {
        mvc.perform(put(DETAIL, 100).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(skuService, generateService);
    }

    @Test
    @DisplayName("修改接口拒绝513字符图片")
    void update_rejectsOversizedImage() throws Exception {
        String body = "{\"price\":1.00,\"imageUrl\":\"" + "a".repeat(513) + "\",\"version\":3}";
        mvc.perform(put(DETAIL, 100).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(10001));
        verifyNoInteractions(skuService, generateService);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("修改接口拒绝非正数SKU ID")
    void update_rejectsInvalidId(long id) throws Exception {
        mvc.perform(put(DETAIL, id).contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(10001));
        verifyNoInteractions(skuService, generateService);
    }

    @ParameterizedTest
    @EnumSource(value = ProductErrorCode.class, names = {"SKU_NOT_FOUND", "SPU_NOT_FOUND", "SPU_NOT_DRAFT",
            "SKU_VERSION_CONFLICT", "SKU_UPDATE_FAILED"})
    @DisplayName("修改接口保留存在性、状态、版本冲突和更新失败的业务码")
    void update_returnsBusinessErrors(ProductErrorCode error) throws Exception {
        doThrow(new BizException(error)).when(skuService).updateSku(eq(100L), any(SkuUpdateDTO.class));
        mvc.perform(put(DETAIL, 100).contentType(MediaType.APPLICATION_JSON).content(UPDATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(error.getCode()))
                .andExpect(jsonPath("$.message").value(error.getMessage()))
                .andExpect(jsonPath("$.success").value(false));
        verify(skuService).updateSku(eq(100L), any(SkuUpdateDTO.class));
    }

    @Test
    @DisplayName("生成路由绑定路径SPU ID和JSON价格图片，返回生成的ID列表")
    void generate_bindsPathAndBody() throws Exception {
        when(generateService.generate(eq(42L), any(SkuGenerateDTO.class), eq(0L))).thenReturn(List.of(100L, 101L));
        mvc.perform(post(GENERATE, 42).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"price":99.90,"imageUrl":"https://example.test/sku.png"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0]").value(100))
                .andExpect(jsonPath("$.data[1]").value(101));
        var dto = ArgumentCaptor.forClass(SkuGenerateDTO.class);
        verify(generateService).generate(eq(42L), dto.capture(), eq(0L));
        assertEquals(new BigDecimal("99.90"), dto.getValue().getPrice());
        assertEquals("https://example.test/sku.png", dto.getValue().getImageUrl());
        verifyNoInteractions(skuService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "99999999.99"})
    @DisplayName("生成接口接受零元和最大合法价格，图片可不传")
    void generate_acceptsPriceBoundaries(String price) throws Exception {
        when(generateService.generate(eq(42L), any(), eq(0L))).thenReturn(List.of(100L));
        mvc.perform(post(GENERATE, 42).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":" + price + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        var dto = ArgumentCaptor.forClass(SkuGenerateDTO.class);
        verify(generateService).generate(eq(42L), dto.capture(), eq(0L));
        assertEquals(new BigDecimal(price), dto.getValue().getPrice());
        assertNull(dto.getValue().getImageUrl());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"price\":null}", "{\"price\":-0.01}",
            "{\"price\":1.001}", "{\"price\":100000000.00}", "{broken"})
    @DisplayName("非法价格和损坏JSON返回参数错误，不调用生成Service")
    void generate_rejectsInvalidBody(String body) throws Exception {
        mvc.perform(post(GENERATE, 42).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(generateService, skuService);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("生成与列表接口拒绝非正数路径ID，不进入Service")
    void routes_rejectNonPositiveSpuId(long id) throws Exception {
        mvc.perform(get(LIST, id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001));
        mvc.perform(post(GENERATE, id).contentType(MediaType.APPLICATION_JSON).content("{\"price\":1.00}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(10001));
        verifyNoInteractions(generateService, skuService);
    }

    @Test
    @DisplayName("复数skus列表路由正确绑定SPU ID并序列化VO，而非空对象")
    void list_serializesVo() throws Exception {
        var vo = new SkuListVO();
        vo.setId(100L); vo.setSpuId(42L); vo.setSkuCode("SKU100");
        vo.setSpecValues(List.of()); vo.setSpecText("默认规格");
        vo.setPrice(new BigDecimal("99.90")); vo.setImageUrl("https://example.test/sku.png");
        vo.setIsDefault(true); vo.setVersion(3);
        when(skuService.listSku(42L)).thenReturn(List.of(vo));
        mvc.perform(get(LIST, 42))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(100))
                .andExpect(jsonPath("$.data[0].spuId").value(42))
                .andExpect(jsonPath("$.data[0].skuCode").value("SKU100"))
                .andExpect(jsonPath("$.data[0].specValues").isEmpty())
                .andExpect(jsonPath("$.data[0].specText").value("默认规格"))
                .andExpect(jsonPath("$.data[0].price").value(99.90))
                .andExpect(jsonPath("$.data[0].imageUrl").value(vo.getImageUrl()))
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[0].version").value(3))
                .andExpect(jsonPath("$.data[0].specHash").doesNotExist())
                .andExpect(jsonPath("$.data[0].deleted").doesNotExist());
        verify(skuService).listSku(42L);
        verifyNoInteractions(generateService);
    }

    @Test
    @DisplayName("未生成SKU时HTTP返回成功和空数组")
    void list_returnsEmptyArray() throws Exception {
        when(skuService.listSku(42L)).thenReturn(List.of());
        mvc.perform(get(LIST, 42)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
        verify(skuService).listSku(42L);
    }

    @Test
    @DisplayName("SPU不存在时经统一异常处理返回业务码，区别于空列表")
    void list_returnsBusinessError() throws Exception {
        when(skuService.listSku(42L)).thenThrow(new BizException(ProductErrorCode.SPU_NOT_FOUND));
        mvc.perform(get(LIST, 42)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ProductErrorCode.SPU_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("重复生成时经统一异常处理保留业务错误码")
    void generate_returnsBusinessError() throws Exception {
        when(generateService.generate(eq(42L), any(), eq(0L)))
                .thenThrow(new BizException(ProductErrorCode.SKU_ALREADY_GENERATED));
        mvc.perform(post(GENERATE, 42).contentType(MediaType.APPLICATION_JSON).content("{\"price\":1.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ProductErrorCode.SKU_ALREADY_GENERATED.getCode()))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("详情路由绑定SKU ID，返回完整对象、规格及审计字段，不泄露内部字段")
    void detail_bindsSkuIdAndSerializesVo() throws Exception {
        var vo = new SkuDetailVO();
        var color = new SkuSpecValueDTO();
        color.setName("颜色"); color.setValue("黑色");
        vo.setId(100L); vo.setSpuId(42L); vo.setSkuCode("SKU100");
        vo.setSpecValues(List.of(color)); vo.setSpecText("颜色=黑色");
        vo.setPrice(new BigDecimal("99.90")); vo.setImageUrl("https://example.test/sku.png");
        vo.setIsDefault(false); vo.setVersion(3);
        vo.setCreatedBy(7L); vo.setUpdatedBy(8L);
        vo.setCreatedAt(LocalDateTime.of(2026, 10, 4, 10, 0));
        vo.setUpdatedAt(vo.getCreatedAt().plusHours(1));
        when(skuService.detailSku(100L)).thenReturn(vo);

        mvc.perform(get(DETAIL, 100))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.spuId").value(42))
                .andExpect(jsonPath("$.data.skuCode").value("SKU100"))
                .andExpect(jsonPath("$.data.specValues[0].name").value("颜色"))
                .andExpect(jsonPath("$.data.specValues[0].value").value("黑色"))
                .andExpect(jsonPath("$.data.specText").value("颜色=黑色"))
                .andExpect(jsonPath("$.data.price").value(99.90))
                .andExpect(jsonPath("$.data.imageUrl").value(vo.getImageUrl()))
                .andExpect(jsonPath("$.data.isDefault").value(false))
                .andExpect(jsonPath("$.data.version").value(3))
                .andExpect(jsonPath("$.data.createdBy").value(7))
                .andExpect(jsonPath("$.data.updatedBy").value(8))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.updatedAt").exists())
                .andExpect(jsonPath("$.data.specHash").doesNotExist())
                .andExpect(jsonPath("$.data.deleted").doesNotExist());
        verify(skuService).detailSku(100L);
        verifyNoMoreInteractions(skuService);
        verifyNoInteractions(generateService);
    }

    @Test
    @DisplayName("默认SKU详情通过HTTP返回空规格数组而不是null")
    void detail_defaultSkuSerializesEmptyArray() throws Exception {
        var vo = new SkuDetailVO();
        vo.setId(100L); vo.setSpecValues(List.of());
        vo.setIsDefault(true); vo.setSpecText("默认规格");
        when(skuService.detailSku(100L)).thenReturn(vo);
        mvc.perform(get(DETAIL, 100)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.specValues").isArray())
                .andExpect(jsonPath("$.data.specValues").isEmpty())
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andExpect(jsonPath("$.data.specText").value("默认规格"));
        verify(skuService).detailSku(100L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("详情接口拒绝非正数SKU ID，不调用Service")
    void detail_rejectsNonPositiveSkuId(long id) throws Exception {
        mvc.perform(get(DETAIL, id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(skuService, generateService);
    }

    @ParameterizedTest
    @EnumSource(value = ProductErrorCode.class, names = {"SKU_NOT_FOUND", "SPU_NOT_FOUND"})
    @DisplayName("详情接口通过统一异常处理保留SKU和所属SPU不存在的业务码")
    void detail_returnsBusinessErrors(ProductErrorCode errorCode) throws Exception {
        when(skuService.detailSku(100L)).thenThrow(new BizException(errorCode));
        mvc.perform(get(DETAIL, 100)).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(errorCode.getCode()))
                .andExpect(jsonPath("$.message").value(errorCode.getMessage()))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(skuService).detailSku(100L);
        verifyNoInteractions(generateService);
    }
}
