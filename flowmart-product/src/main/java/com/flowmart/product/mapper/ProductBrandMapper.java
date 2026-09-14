package com.flowmart.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flowmart.product.dto.BrandQueryDTO;
import com.flowmart.product.entity.ProductBrand;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ProductBrandMapper extends BaseMapper<ProductBrand> {

    /** 检查其他未删除品牌是否占用该名称，供修改品牌使用。 */
    boolean existsByNameExcludingId(@Param("name") String name,
                                  @Param("excludeId") Long excludeId);

    /**
     *
     * @param name 品牌名称
     * @param deleted 逻辑删除
     * @return
     */
    boolean existsByNameAndDeleted(@Param("name") String name,
                                   @Param("deleted") Long deleted);

    /**
     * 分页查询品牌
     *
     * @param query  查询条件
     * @param offset 偏移量
     * @param limit  每页条数
     * @return 品牌列表
     */
    List<ProductBrand> selectBrandPage(@Param("query") BrandQueryDTO query,
                                  @Param("offset") long offset,
                                  @Param("limit") int limit);

    /**
     * 统计符合条件的品牌数量
     *
     * @param query 查询条件
     * @return 总数
     */
    long countByQuery(@Param("query") BrandQueryDTO query);

}
