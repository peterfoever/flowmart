package com.flowmart.product.convert;

import com.flowmart.product.dto.CreateBrandDTO;
import com.flowmart.product.dto.UpdateBrandDTO;
import com.flowmart.product.dto.UpdateCategoryDTO;
import com.flowmart.product.entity.ProductBrand;
import com.flowmart.product.entity.ProductCategory;
import com.flowmart.product.vo.BrandVO;
import com.flowmart.product.vo.CategoryDetailVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * 品牌类目转换器
 */
@Mapper(componentModel = "spring")
public interface BrandConverter {

    /** 将新增请求转换为待持久化实体；后端负责生成 ID。 */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "version", ignore = true)
    ProductBrand toEntity(CreateBrandDTO createBrandDTO);

    /** 将允许编辑的字段覆盖到已有实体。 */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "status", ignore = true)
    void updateEntity(UpdateBrandDTO dto, @MappingTarget ProductBrand entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "version", ignore = true)
    ProductBrand toEntity(UpdateBrandDTO updateBrandDTO);

    /** 实体转换为详情响应；额外展示字段由 Service 查询后补充。 */
    @Mapping(target = "statusText", ignore = true)
    BrandVO toDetailVO(ProductBrand entity);

    /**
     * 实体列表 → VO 列表
     */
    List<BrandVO> toVOList(List<ProductBrand> entities);
}
