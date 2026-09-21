package com.flowmart.product.convert;

import com.flowmart.product.dto.CreateSpuDTO;
import com.flowmart.product.dto.UpdateCategoryDTO;
import com.flowmart.product.dto.UpdateSpuDTO;
import com.flowmart.product.entity.ProductCategory;
import com.flowmart.product.entity.ProductSpu;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * spu商品对象转换器
 */
@Mapper(componentModel = "spring")
public interface SpuConverter {
    /** 将新增请求转换为待持久化实体；后端负责生成 ID 与编码。 */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "spuCode", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "version", ignore = true)
    ProductSpu toEntity(CreateSpuDTO dto);

    /** 将允许编辑的字段覆盖到已有实体。 */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "spuCode", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(UpdateSpuDTO dto, @MappingTarget ProductSpu entity);
}
