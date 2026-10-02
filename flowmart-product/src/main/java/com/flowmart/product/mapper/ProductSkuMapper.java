package com.flowmart.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.flowmart.product.entity.ProductSku;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ProductSkuMapper extends BaseMapper<ProductSku> {
    // ProductSkuMapper：一次插入一批 SKU
    int batchInsert(@Param("items") List<ProductSku> items);

    default int countActiveBySpuId(Long spuId) {
        return Math.toIntExact(selectCount(Wrappers.<ProductSku>lambdaQuery()
                .eq(ProductSku::getSpuId, spuId)));
    }
}
