package com.kedu.tago.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** MyBatis 매퍼 스캔 설정 */
@Configuration
@MapperScan("com.kedu.tago")
public class MyBatisConfig {
}
