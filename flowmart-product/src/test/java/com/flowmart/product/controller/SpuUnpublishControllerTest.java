package com.flowmart.product.controller;

import com.flowmart.common.exception.BizException;
import com.flowmart.common.web.GlobalExceptionHandler;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.service.SpuService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实路由、请求校验和错误封装；Service为Mock，不连接数据库。 */
class SpuUnpublishControllerTest {
    private SpuService service;
    private MockMvc mvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        service = mock(SpuService.class);
        validator = new LocalValidatorFactoryBean(); validator.afterPropertiesSet();
        var proxy = new ProxyFactory(new SpuController(service)); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new MethodValidationInterceptor(validator.getValidator()));
        mvc = MockMvcBuilders.standaloneSetup(proxy.getProxy()).setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void closeValidator() { validator.close(); }

    @ParameterizedTest
    @ValueSource(ints = {0, 7, 128})
    @DisplayName("下架路由可达，JSON对象版本正确传入，不被查询参数覆盖")
    void validBody_callsUnpublish(int version) throws Exception {
        mvc.perform(post("/api/product/admin/spus/42/unpublish").param("version", "99")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.success").value(true));
        verify(service).unpublish(42L, version);
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"version\":null}", "{\"version\":-1}",
            "{\"version\":\"abc\"}", "{\"version\":2147483648}", "0", "null", "[]", "{"})
    @DisplayName("下架缺少请求体、非法版本或错误JSON结构被拒绝")
    void invalidBody_rejected(String body) throws Exception {
        mvc.perform(post("/api/product/admin/spus/42/unpublish")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("下架非法路径ID不调用Service")
    void invalidId_rejected(long id) throws Exception {
        mvc.perform(post("/api/product/admin/spus/{id}/unpublish", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(jsonPath("$.code").value(10001));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @EnumSource(value = ProductErrorCode.class, names = {"SPU_VERSION_CONFLICT", "SPU_NOT_ON_SHELF"})
    @DisplayName("下架版本与状态错误保留业务错误码，不伪装成成功")
    void businessFailure_preservesCode(ProductErrorCode error) throws Exception {
        doThrow(new BizException(error)).when(service).unpublish(42L, 0);
        mvc.perform(post("/api/product/admin/spus/42/unpublish")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(jsonPath("$.code").value(error.getCode()))
                .andExpect(jsonPath("$.success").value(false));
    }
}
