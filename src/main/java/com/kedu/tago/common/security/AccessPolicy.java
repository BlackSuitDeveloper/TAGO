package com.kedu.tago.common.security;

import com.kedu.tago.member.Role;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;

import static com.kedu.tago.common.ApiPaths.V1;

/** 경로 기준 접근 정책 (공개 요청 판별, /api/v1/admin/** 은 ADMIN, 나머지는 USER) */
@Component
public class AccessPolicy {

    private record Route(HttpMethod method, String pattern) {}

    /** 로그인 없이 쓸 수 있는 요청 */
    private static final List<Route> PUBLIC = List.of(
            new Route(HttpMethod.POST, V1 + "/member"),            // 회원가입
            new Route(HttpMethod.GET, V1 + "/member/checkId"),
            new Route(HttpMethod.POST, V1 + "/auth/**"),           // 아이디 찾기, 비밀번호 재설정(이메일 인증)
            new Route(HttpMethod.GET, V1 + "/station"),
            new Route(HttpMethod.GET, V1 + "/schedule/**"),
            new Route(HttpMethod.GET, V1 + "/seat/**")             // SSE(EventSource)는 인증 헤더를 못 붙인다
    );

    private static final String ADMIN_PATTERN = V1 + "/admin/**";

    private final AntPathMatcher matcher = new AntPathMatcher();

    /** 로그인 없이 접근 가능한 요청인지 여부 */
    public boolean isPublic(String httpMethod, String path) {
        return PUBLIC.stream().anyMatch(r ->
                r.method().name().equalsIgnoreCase(httpMethod) && matcher.match(r.pattern(), path));
    }

    /** 경로만으로 정해지는 필요 권한 */
    public Role requiredRole(String path) {
        return matcher.match(ADMIN_PATTERN, path) ? Role.ADMIN : Role.USER;
    }
}
