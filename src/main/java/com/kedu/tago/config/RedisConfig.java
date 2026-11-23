package com.kedu.tago.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    private final StringRedisTemplate redis;

    public void setValue(String key, String value) {
        redis.opsForValue().set(key, value);
    }

    public void setValue(String key, String value, Long expiration) {
        redis.opsForValue().set(key, value, Duration.ofMillis(expiration));
    }

    public String getValue(String key) {
        return redis.opsForValue().get(key);
    }

    public void deleteValue(String key) {
        redis.delete(key);
    }
}
