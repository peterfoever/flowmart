package com.flowmart.product.entity;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * SPU 编码生成器
 * <p>
 * 格式：SPU + yyyyMMdd + 6位序列，如 SPU20260921000001
 * 用 Redis INCR 保证并发安全
 */
@Component
@RequiredArgsConstructor
public class SpuCodeGenerator {
    private static final String PREFIX = "SPU";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String REDIS_KEY_PREFIX = "spu:code:seq:";
    private final StringRedisTemplate redisTemplate;

    /**
     * 生成下一个 SPU 编码
     */
    public String next() {
        String date = LocalDate.now().format(DATE_FORMAT);
        String key = REDIS_KEY_PREFIX + date;

        // INCR 原子操作，保证并发安全
        Long seq = redisTemplate.opsForValue().increment(key);

        // 设置过期时间：2 天，避免 Redis 堆积无用 key
        if (seq != null && seq == 1L) {
            redisTemplate.expire(key, java.time.Duration.ofDays(2));
        }
        return PREFIX + date + String.format("%06d", seq);
    }
}
