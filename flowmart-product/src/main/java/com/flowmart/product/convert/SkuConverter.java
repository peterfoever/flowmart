package com.flowmart.product.convert;


import com.flowmart.product.dto.SkuGenerateDTO;
import com.flowmart.product.entity.ProductSku;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SkuConverter {
    /** 将新增请求转换为待持久化实体；后端负责生成 ID 与编码。 */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "skuCode", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "version", ignore = true)
    ProductSku toEntity(SkuGenerateDTO dto);
}
