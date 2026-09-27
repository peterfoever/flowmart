package com.flowmart.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmart.product.convert.SpuConverter;
import com.flowmart.product.dto.SpecDTO;
import com.flowmart.product.entity.ProductSpu;
import com.flowmart.product.service.SpuService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证方法约束与 JSON 契约，不替代 HTTP/Spring MVC 集成测试。 */
class SpuDetailContractTest {
    @Test
    void idConstraint_rejectsNullZeroAndNegative() throws Exception {
        SpuService service = mock(SpuService.class);
        SpuController controller = new SpuController(service);
        var method = SpuController.class.getMethod("getSpuDetail", Long.class);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator().forExecutables();
            for (Long id : new Long[]{null, 0L, -1L}) {
                assertFalse(validator.validateParameters(controller, method, new Object[]{id}).isEmpty());
            }
            assertTrue(validator.validateParameters(controller, method, new Object[]{1L}).isEmpty());
        }
        verifyNoInteractions(service);
    }

    @Test
    void response_serializesNestedArraysAndWrapsServiceResult() throws Exception {
        ProductSpu entity = new ProductSpu();
        entity.setId(123L);
        entity.setCarouselImages(List.of("a.png", "b.png"));
        SpecDTO spec = new SpecDTO();
        spec.setName("颜色");
        spec.setValues(List.of("黑", "白"));
        entity.setSpecs(List.of(spec));
        var vo = Mappers.getMapper(SpuConverter.class).toDetailVO(entity);
        SpuService service = mock(SpuService.class);
        when(service.getDetailById(123L)).thenReturn(vo);
        var response = new SpuController(service).getSpuDetail(123L);
        var json = new ObjectMapper().findAndRegisterModules().readTree(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(response));
        assertEquals(0, json.get("code").asInt());
        assertTrue(json.at("/data/carouselImages").isArray());
        assertTrue(json.at("/data/specs").isArray());
        assertEquals("颜色", json.at("/data/specs/0/name").asText());
        assertEquals("白", json.at("/data/specs/0/values/1").asText());
        verify(service).getDetailById(123L);
    }
}
