package com.flowmart.product.dto;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BrandQueryDTOTest {
    @Test
    void defaultsAndCombinedFiltersAreValid() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            BrandQueryDTO query = new BrandQueryDTO();
            assertTrue(validator.validate(query).isEmpty());
            query.setName("华");
            query.setInitial("H");
            query.setStatus(0);
            assertTrue(validator.validate(query).isEmpty());
        }
    }

    @Test
    void invalidPagingAndFiltersAreRejected() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            BrandQueryDTO query = new BrandQueryDTO();
            query.setPage(0);
            query.setSize(101);
            query.setStatus(2);
            query.setInitial("ab");
            query.setName("a".repeat(65));
            assertEquals(5, validator.validate(query).size());
            query = new BrandQueryDTO();
            query.setPage(null);
            query.setSize(null);
            assertEquals(2, validator.validate(query).size());
            query.setPage(1);
            query.setSize(0);
            assertEquals(1, validator.validate(query).size());
        }
    }
}
