package com.kedu.tago.config;

import com.kedu.tago.member.Role;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * 경로 기준의 접근 정책: 어떤 요청이 로그인 없이 가능한지, 경로만으로 정해지는 필요 권한이 무엇인지.
 *
 * <ul>
 *   <li>공개: 아래 PUBLIC 목록(메서드까지 일치해야 한다)</li>
 *   <li>/admin/** : ADMIN (경로 규칙. 컨트롤러에 @RequireRole 을 빠뜨려도 막히는 안전장치)</li>
 *   <li>그 밖의 모든 요청: 로그인한 회원(USER 이상). 새 API 는 아무 설정 없이 기본적으로 보호된다.</li>
 * </ul>
 *
 * 개별 API 의 권한은 컨트롤러에 {@code @RequireRole(Role.ADMIN)} 으로 선언한다(AuthInterceptor 가 함께 검사).
 */
@Component
public class AccessPolicy {

    private record Route(HttpMethod method, String pattern) {}

    /** 로그인 없이 쓸 수 있는 요청 (경로는 context-path(/api) 이후 기준) */
    private static final List<Route> PUBLIC = List.of(
            new Route(HttpMethod.GET, "/ping"),
            new Route(HttpMethod.POST, "/member"),            // 회원가입
            new Route(HttpMethod.POST, "/member/login"),
            new Route(HttpMethod.GET, "/member/checkId"),
            new Route(HttpMethod.POST, "/auth/**"),           // 아이디 찾기, 비밀번호 재설정(이메일 인증)
            new Route(HttpMethod.GET, "/station"),
            new Route(HttpMethod.GET, "/schedule/**"),
            new Route(HttpMethod.GET, "/seat/**")             // 좌석 조회 + SSE(EventSource 는 헤더를 못 붙인다)
    );

    private static final String ADMIN_PATTERN = "/admin/**";

    private final AntPathMatcher matcher = new AntPathMatcher();

    public boolean isPublic(String httpMethod, String path) {
        return PUBLIC.stream().anyMatch(r ->
                r.method().name().equalsIgnoreCase(httpMethod) && matcher.match(r.pattern(), path));
    }

    /** 경로만으로 정해지는 필요 권한 (/admin/** 은 ADMIN, 나머지는 USER) */
    public Role requiredRole(String path) {
        return matcher.match(ADMIN_PATTERN, path) ? Role.ADMIN : Role.USER;
    }
}
