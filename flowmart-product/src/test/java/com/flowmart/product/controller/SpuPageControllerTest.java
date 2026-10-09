package com.flowmart.product.controller;

import com.flowmart.common.web.GlobalExceptionHandler;
import com.flowmart.product.convert.SpuConverter;
import com.flowmart.product.generator.SpuCodeGenerator;
import com.flowmart.product.mapper.*;
import com.flowmart.product.service.impl.SpuServiceImpl;
import com.flowmart.product.vo.SpuListVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP 参数绑定、校验、真实 Service 和错误封装；Mapper 为 Mock，不启动数据库。 */
class SpuPageControllerTest {
    private static final String URL = "/api/product/admin/spus";
    private ProductSpuMapper spus;
    private ProductCategoryMapper categories;
    private ProductBrandMapper brands;
    private LocalValidatorFactoryBean validator;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        spus = mock(ProductSpuMapper.class); categories = mock(ProductCategoryMapper.class);
        brands = mock(ProductBrandMapper.class);
        var service = new SpuServiceImpl(mock(SpuConverter.class), spus, categories, brands,
                mock(SpuCodeGenerator.class), mock(ProductSkuMapper.class));
        validator = new LocalValidatorFactoryBean(); validator.afterPropertiesSet();
        var proxy = new ProxyFactory(new SpuController(service)); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new MethodValidationInterceptor(validator.getValidator()));
        mvc = MockMvcBuilders.standaloneSetup(proxy.getProxy()).setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void closeValidator() { validator.close(); }

    @Test
    @DisplayName("HTTP省略参数使用默认分页及统一返回结构")
    void omittedParameters_useDefaults_andReturnPageContract() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pageNum").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records").isEmpty());
        verify(spus).countSpu(any(), isNull(), isNull()); verifyNoMoreInteractions(spus);
    }

    @ParameterizedTest
    @CsvSource(value = {"pageNum|", "pageSize|", "pageNum|abc", "pageSize|abc", "pageNum|0",
            "pageSize|0", "pageSize|201", "pageNum|2147483648", "pageSize|2147483648",
            "pageNum|-1", "pageSize|-1", "status|-1", "status|3", "status|draft",
            "categoryId|0", "categoryId|-1", "categoryId|abc", "brandId|0", "brandId|-1",
            "brandId|NONE", "brandId|9223372036854775808", "noBrand|abc",
            "createdFrom|2026-02-30 00:00:00", "createdTo|2026-10-09T00:00:00"}, delimiter = '|')
    @DisplayName("HTTP非法参数返回10001且不访问数据库")
    void invalidQueryParameters_return10001_withoutDatabaseQuery(String field, String value) throws Exception {
        mvc.perform(get(URL).param(field, value == null ? "" : value))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(10001))
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(spus, categories, brands);
    }

    @ParameterizedTest
    @ValueSource(strings = {"keyword", "spuCode"})
    @DisplayName("HTTP拒绝超长关键词及编码")
    void overlongText_rejected(String field) throws Exception {
        mvc.perform(get(URL).param(field, "x".repeat(field.equals("keyword") ? 129 : 65)))
                .andExpect(jsonPath("$.code").value(10001));
        verifyNoInteractions(spus, categories, brands);
    }

    @Test
    @DisplayName("HTTP拒绝品牌冲突与相等时间边界")
    void crossFieldConflicts_rejected() throws Exception {
        mvc.perform(get(URL).param("brandId", "2").param("noBrand", "true"))
                .andExpect(jsonPath("$.code").value(10001));
        mvc.perform(get(URL).param("createdFrom", "2026-10-09 00:00:00").param("createdTo", "2026-10-09 00:00:00"))
                .andExpect(jsonPath("$.code").value(10001));
        verifyNoInteractions(spus, categories, brands);
    }

    @ParameterizedTest
    @ValueSource(strings = {"categoryId", "brandId"})
    @DisplayName("HTTP不存在的类目或品牌筛选返回参数错误")
    void nonexistentFilter_returnsParamInvalid(String field) throws Exception {
        mvc.perform(get(URL).param(field, "99")).andExpect(jsonPath("$.code").value(10001));
        verifyNoInteractions(spus);
    }

    @ParameterizedTest
    @ValueSource(strings = {"createdFrom", "createdTo"})
    @DisplayName("HTTP支持单端创建时间")
    void oneSidedTime_isAccepted(String field) throws Exception {
        mvc.perform(get(URL).param(field, "2026-10-09 00:00:00"))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("HTTP状态0与无品牌绑定正确且摘要不含大字段")
    void statusZeroAndNoBrand_bindCorrectly_andResponseExcludesHeavyFields() throws Exception {
        var vo = new SpuListVO(); vo.setId(1L); vo.setSpuCode("SPU1"); vo.setName("手机");
        vo.setMainImageUrl("main.png"); vo.setCategoryId(3L); vo.setCategoryName("数码");
        vo.setStatus(0); vo.setVersion(2); vo.setCreatedAt(LocalDateTime.of(2026, 10, 9, 12, 0));
        vo.setUpdatedAt(vo.getCreatedAt());
        when(spus.countSpu(any(), isNull(), isNull())).thenReturn(1L);
        when(spus.selectSpuPage(any(), isNull(), isNull(), eq(0L))).thenReturn(List.of(vo));
        mvc.perform(get(URL).param("status", "0").param("noBrand", "true").param("pageSize", "200"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.pageSize").value(200))
                .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.records[0].id").value(1))
                .andExpect(jsonPath("$.data.records[0].spuCode").value("SPU1"))
                .andExpect(jsonPath("$.data.records[0].name").value("手机"))
                .andExpect(jsonPath("$.data.records[0].mainImageUrl").value("main.png"))
                .andExpect(jsonPath("$.data.records[0].categoryId").value(3))
                .andExpect(jsonPath("$.data.records[0].categoryName").value("数码"))
                .andExpect(jsonPath("$.data.records[0].brandText").value("无品牌"))
                .andExpect(jsonPath("$.data.records[0].status").value(0))
                .andExpect(jsonPath("$.data.records[0].statusText").value("草稿"))
                .andExpect(jsonPath("$.data.records[0].version").value(2))
                .andExpect(jsonPath("$.data.records[0].createdAt").exists())
                .andExpect(jsonPath("$.data.records[0].updatedAt").exists())
                .andExpect(jsonPath("$.data.records[0].description").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].specs").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].carouselImages").doesNotExist());
        verify(spus).countSpu(argThat(q -> q.getStatus() == 0 && q.getNoBrand() && q.getPageSize() == 200), isNull(), isNull());
    }
}
