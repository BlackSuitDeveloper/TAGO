package com.kedu.tago.config;

import com.kedu.tago.interceptor.LoginValidator;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class InterceptorConfig implements WebMvcConfigurer {

    private LoginValidator lv;
    InterceptorConfig(LoginValidator validator) {
        this.lv = validator;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(lv)
                .addPathPatterns("/**") // recursive ex) localhost/member/... 전부 포함
                .excludePathPatterns("/auth/login", "/user/signup", "/user/checkId");


    }
}
