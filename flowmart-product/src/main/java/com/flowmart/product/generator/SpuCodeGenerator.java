package com.flowmart.product.generator;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


/**
 * SPU 编码生成器
 * <p>
 * 当前方案：SPU + 雪花ID
 * <ul>
 *   <li>复用 MyBatis-Plus ID 生成器，不依赖 Redis</li>
 *   <li>多实例部署需确保节点标识不冲突，数据库唯一索引最终兜底</li>
 * </ul>
 * <p>
 * 未来如需更短的可读编码，可切换到"SPU + 日期 + Redis 自增"方案，
 * 但必须保留数据库唯一索引 uk_spu_code 作为最后防线。
 */
@Component
@Slf4j
public class SpuCodeGenerator {
    private static final String PREFIX = "SPU";
    /**
     * 生成下一个 SPU 编码
     * <p>
     * 格式：SPU + 雪花ID，如 SPU1734567890123456789
     *
     * @return SPU 编码
     */
    public String next() {
        String spuCode = PREFIX + IdWorker.getId();
        log.debug("生成SPU编码: {}", spuCode);
        return spuCode;
    }
}
