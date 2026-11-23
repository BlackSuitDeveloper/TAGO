package com.kedu.tago.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/** Redis 문자열 값 저장/조회 헬퍼 */
@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    private final StringRedisTemplate redis;

    /** 값 저장 */
    public void setValue(String key, String value) {
        redis.opsForValue().set(key, value);
    }

    /** 값 저장 (만료 시간 ms) */
    public void setValue(String key, String value, Long expiration) {
        redis.opsForValue().set(key, value, Duration.ofMillis(expiration));
    }

    /** 값 조회 */
    public String getValue(String key) {
        return redis.opsForValue().get(key);
    }

    /** 값 삭제 */
    public void deleteValue(String key) {
        redis.delete(key);
    }
}
