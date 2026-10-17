package com.flowmart.product.controller;

import com.flowmart.common.exception.BizException;
import com.flowmart.common.web.GlobalExceptionHandler;
import com.flowmart.product.enums.ProductErrorCode;
import com.flowmart.product.service.SpuService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

/** 使用实际 Controller、校验器及异常封装，不连接数据库。 */
class SpuPublishControllerTest {
    private SpuService service;
    private MockMvc mvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        service = mock(SpuService.class);
        validator = new LocalValidatorFactoryBean(); validator.afterPropertiesSet();
        var proxy = new ProxyFactory(new SpuController(service));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new MethodValidationInterceptor(validator.getValidator()));
        mvc = MockMvcBuilders.standaloneSetup(proxy.getProxy()).setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void closeValidator() { validator.close(); }

    @ParameterizedTest
    @ValueSource(ints = {0, 7})
    @DisplayName("JSON对象中的version正确传给Service，查询参数不能覆盖请求体")
    void jsonObject_bindsVersion(int version) throws Exception {
        mvc.perform(post("/api/product/admin/spus/42/publish").param("version", "99")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.success").value(true));
        verify(service).publish(42L, version);
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"version\":null}", "{\"version\":-1}",
            "{\"version\":\"abc\"}", "{\"version\":2147483648}", "0", "null", "[]", "{"})
    @DisplayName("请求体缺失、非法版本或错误JSON结构拒绝且不调用Service")
    void invalidBody_rejected(String body) throws Exception {
        mvc.perform(post("/api/product/admin/spus/42/publish")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("非正数路径ID拒绝且不调用Service")
    void invalidId_rejected(long id) throws Exception {
        mvc.perform(post("/api/product/admin/spus/{id}/publish", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(jsonPath("$.code").value(10001));
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("业务版本冲突保留错误码，不包装为成功")
    void businessFailure_preservesCode() throws Exception {
        doThrow(new BizException(ProductErrorCode.SPU_VERSION_CONFLICT)).when(service).publish(42L, 0);
        mvc.perform(post("/api/product/admin/spus/42/publish")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(jsonPath("$.code").value(ProductErrorCode.SPU_VERSION_CONFLICT.getCode()))
                .andExpect(jsonPath("$.success").value(false));
    }
}
