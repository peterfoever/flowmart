package com.flowmart.product.convert;

import com.flowmart.product.dto.imports.*;
import com.flowmart.product.entity.*;
import com.flowmart.product.vo.imports.*;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ImportConverter {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "spuName")
    @Mapping(target = "categoryId", source = "categoryId")
    @Mapping(target = "brandId", source = "brandId")
    @Mapping(target = "mainImageUrl", source = "mainImage")
    @Mapping(target = "specs", source = "specDefinition")
    ProductSpu toSpu(ImportGroup group);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "price", source = "price")
    @Mapping(target = "imageUrl", source = "skuImage")
    @Mapping(target = "specValues", source = "specCombination")
    ProductSku toSku(ImportRow row);

    @Mapping(target = "batchId", source = "id")
    @Mapping(target = "warnings", source = "warnings", qualifiedByName = "warnings")
    ImportBatchVO toBatch(ImportBatchEntity entity);
    ImportGroupVO toGroup(ImportGroupResultEntity entity);
    ImportRowErrorVO toError(ImportRowErrorEntity entity);

    @Named("warnings")
    default java.util.List<String> warnings(String json) {
        if (json == null) return java.util.List.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("批次warnings不是合法JSON数组", e);
        }
    }
}
