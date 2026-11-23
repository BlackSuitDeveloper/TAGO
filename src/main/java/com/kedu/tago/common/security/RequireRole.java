package com.kedu.tago.common.security;

import com.kedu.tago.member.Role;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** API 호출에 필요한 최소 권한 (클래스 또는 메서드에 지정, 메서드가 우선) */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    Role value();
}
