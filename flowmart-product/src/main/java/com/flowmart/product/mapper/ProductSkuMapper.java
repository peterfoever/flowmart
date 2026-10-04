package com.flowmart.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.flowmart.product.entity.ProductSku;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface ProductSkuMapper extends BaseMapper<ProductSku> {
    // ProductSkuMapper：一次插入一批 SKU
    int batchInsert(@Param("items") List<ProductSku> items);

    default int countActiveBySpuId(Long spuId) {
        return Math.toIntExact(selectCount(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getSpuId, spuId)));
    }

    /**
     * 用请求中的 version 作为条件更新 SKU。
     *
     * 条件：id + deleted=0 + version + spu_id（二次确认归属）。
     * 成功时 version = version + 1。
     * 只更新允许修改的列：price、image_url、updated_by、updated_at。
     */
    int updatePriceAndImage(@Param("id") Long id,
                            @Param("spuId") Long spuId,
                            @Param("version") Integer version,
                            @Param("price") BigDecimal price,
                            @Param("imageUrl") String imageUrl,
                            @Param("actor") Long actor,
                            @Param("now") LocalDateTime now);
}
