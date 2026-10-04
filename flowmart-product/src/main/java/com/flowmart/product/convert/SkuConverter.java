package com.flowmart.product.convert;


import com.flowmart.product.dto.SkuGenerateDTO;
import com.flowmart.product.dto.SkuSpecValueDTO;
import com.flowmart.product.entity.ProductSku;

import com.flowmart.product.vo.SkuDetailVO;
import com.flowmart.product.vo.SkuListVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface SkuConverter {
    /** 将新增请求转换为待持久化实体；后端负责生成 ID 与编码。 */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "skuCode", ignore = true)
    @Mapping(target = "spuId", ignore = true)
    @Mapping(target = "specValues", ignore = true)
    @Mapping(target = "specHash", ignore = true)
    @Mapping(target = "isDefault", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "version", ignore = true)
    ProductSku toEntity(SkuGenerateDTO dto);


    @Mapping(target = "specText", source = "specValues", qualifiedByName = "toSpecText")
    SkuListVO toListVO(ProductSku entity);

    @Mapping(target = "specText", source = "specValues", qualifiedByName = "toSpecText")
    SkuDetailVO toDetailVO(ProductSku entity);

    /** 展示顺序沿用持久化的规格顺序，不使用计算哈希时的排序规则。 */
    @Named("toSpecText")
    default String toSpecText(List<SkuSpecValueDTO> specValues) {
        if (specValues == null || specValues.isEmpty()) {
            return "默认规格";
        }
        return specValues.stream()
                .map(spec -> spec.getName() + "=" + spec.getValue())
                .collect(Collectors.joining(" / "));
    }
}
