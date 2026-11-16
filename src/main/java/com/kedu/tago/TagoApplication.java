package com.kedu.tago;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class TagoApplication {

    public static void main(String[] args) {
        SpringApplication.run(TagoApplication.class, args);
    }

}
