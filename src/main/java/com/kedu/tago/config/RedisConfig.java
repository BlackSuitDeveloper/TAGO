package com.kedu.tago.config;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Redis 문자열 값 저장/조회 헬퍼 */
@Component
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

    /** 값 저장 (만료 시간 지정) */
    public void setValue(String key, String value, long timeout, TimeUnit unit) {
        redis.opsForValue().set(key, value, timeout, unit);
    }

    /** 값 조회 */
    public String getValue(String key) {
        return redis.opsForValue().get(key);
    }

    /** 값 삭제 */
    public void deleteValue(String key) {
        redis.delete(key);
    }

    /** 키 존재 여부 확인 */
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redis.hasKey(key));
    }

    /** 숫자 증가 (INCR) */
    public Long increment(String key) {
        return redis.opsForValue().increment(key);
    }

    /** 만료 시간(TTL) 설정 */
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redis.expire(key, timeout, unit);
    }

    /** 만료 시간(TTL) 조회 */
    public Long getExpire(String key, TimeUnit unit) {
        return redis.getExpire(key, unit);
    }

    public StringRedisTemplate getRedis() {
        return redis;
    }
}
