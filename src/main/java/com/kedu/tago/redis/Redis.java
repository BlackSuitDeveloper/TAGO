package com.kedu.tago.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class Redis {

    private final StringRedisTemplate redis;


    public Redis(StringRedisTemplate redis) {
        this.redis = redis;
    }

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