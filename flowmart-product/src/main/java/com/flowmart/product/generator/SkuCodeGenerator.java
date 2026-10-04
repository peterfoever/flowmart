package com.flowmart.product.generator;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SkuCodeGenerator {
    private static final String PREFIX = "SKU";
    /**
     * 生成下一个 SKU 编码
     * <p>
     * 格式：SKU + 雪花ID，如 SKU1734567890123456789
     *
     * @return SKU 编码
     */
    public String next() {
        String skuCode = PREFIX + IdWorker.getId();
        log.debug("生成SKU编码: {}", skuCode);
        return skuCode;
    }
}
