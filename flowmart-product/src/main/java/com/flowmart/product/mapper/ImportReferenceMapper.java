package com.flowmart.product.mapper;

import com.flowmart.product.entity.ProductCategoryBrand;
import org.apache.ibatis.annotations.*;
import java.util.List;

public interface ImportReferenceMapper {
    @Select("""
        <script>
        SELECT category_id, brand_id FROM product_category_brand
        WHERE deleted = 0 AND category_id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        AND brand_id IN
        <foreach collection="brands" item="id" open="(" separator="," close=")">#{id}</foreach>
        </script>
        """)
    List<ProductCategoryBrand> bindings(@Param("ids") List<Long> categoryIds, @Param("brands") List<Long> brandIds);
}
