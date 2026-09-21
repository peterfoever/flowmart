package com.flowmart.product.generator;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.lang.generator.SnowflakeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * SPU 编码生成器
 * <p>
 * 当前方案：SPU + 雪花ID
 * <ul>
 *   <li>无状态，不依赖 Redis</li>
 *   <li>雪花ID 全局唯一，编码天然唯一</li>
 *   <li>生成失败概率极低（仅时钟回拨时可能抛异常）</li>
 * </ul>
 * <p>
 * 未来如需更短的可读编码，可切换到"SPU + 日期 + Redis 自增"方案，
 * 但必须保留数据库唯一索引 uk_spu_code 作为最后防线。
 */
@Component
@RequiredArgsConstructor
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
        String id = UUID.randomUUID().toString();
        String spuCode = PREFIX + id;
        log.debug("生成SPU编码: {}", spuCode);
        return spuCode;
    }
}
