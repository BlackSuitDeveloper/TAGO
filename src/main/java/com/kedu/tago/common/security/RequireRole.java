package com.kedu.tago.common.security;

import com.kedu.tago.member.Role;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 API(또는 컨트롤러의 모든 API)를 쓰려면 필요한 최소 권한. AuthInterceptor 가 검사한다.
 *
 * <pre>
 * &#64;RequireRole(Role.ADMIN)          // 클래스에 붙이면 그 컨트롤러 전체
 * &#64;RestController
 * public class AdminXxxController { ... }
 *
 * &#64;RequireRole(Role.ADMIN)          // 메서드에 붙이면 그 API 만 (클래스보다 우선)
 * &#64;DeleteMapping("/xxx/{id}")
 * public ResWrapper&lt;Void&gt; delete(...) { ... }
 * </pre>
 *
 * 붙이지 않은 보호 API 는 로그인한 회원(USER 이상)이면 쓸 수 있다. 권한이 부족하면 403(관리자 전용은 2004)을 응답한다.
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    Role value();
}
