package com.kedu.tago.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 좌석 선점용 Redisson 클라이언트 (spring.data.redis 설정을 그대로 사용) */
@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port,
            @Value("${spring.data.redis.database:0}") int database,
            @Value("${spring.data.redis.password:}") String password) {
        Config config = new Config();
        SingleServerConfig server = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database)
                .setConnectionPoolSize(64)
                .setConnectionMinimumIdleSize(16);
        if (password != null && !password.isBlank()) {
            server.setPassword(password);
        }
        return Redisson.create(config);
    }
}
