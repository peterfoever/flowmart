package com.flowmart.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flowmart.product.entity.ProductBrand;
import org.apache.ibatis.annotations.Param;

public interface ProductBrandMapper extends BaseMapper<ProductBrand> {

    /**
     *
     * @param name 品牌名称
     * @param deleted 逻辑删除
     * @return
     */
    boolean existsByNameAndDeleted(@Param("name") String name,
                                   @Param("deleted") Long deleted);
}
