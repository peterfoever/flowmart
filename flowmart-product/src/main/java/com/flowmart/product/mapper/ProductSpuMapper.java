package com.flowmart.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flowmart.product.dto.SpecDTO;
import com.flowmart.product.entity.ProductSpu;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ProductSpuMapper extends BaseMapper<ProductSpu> {

    /**
     * 更新 SPU 草稿（带乐观锁）
     * <li>WHERE 带 status = 0，防止修改过程中被上架</li>
     *
     * @return
     */
    int updateDraftById(@Param("id") Long id,
                        @Param("version") Integer version,
                        @Param("name") String name,
                        @Param("categoryId") Long categoryId,
                        @Param("brandId") Long brandId,
                        @Param("mainImageUrl") String mainImageUrl,
                        @Param("carouselImages") List<String> carouselImages,
                        @Param("specs") List<SpecDTO> specs,
                        @Param("description") String description,
                        @Param("updatedBy") Long updatedBy);

}
