package com.kedu.tago.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 어떤 요청이 로그인 없이 가능한지, 관리자만 가능한지를 한 곳에서 정한다.
 *
 * <ul>
 *   <li>공개: 아래 PUBLIC 목록(메서드까지 일치해야 한다)</li>
 *   <li>관리자 전용: /admin/** (로그인 + 관리자 아이디)</li>
 *   <li>그 밖의 모든 요청: 로그인 필요. 새 API 는 아무 설정 없이 기본적으로 보호된다.</li>
 * </ul>
 *
 * 관리자 아이디는 설정 {@code tago.admin.member-ids} (환경변수 TAGO_ADMIN_MEMBER_IDS, 쉼표로 구분)로 지정한다.
 * 비워 두면 관리자가 없는 것으로 보고 /admin/** 는 아무도 쓸 수 없다.
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
    private final Set<String> adminIds;

    public AccessPolicy(@Value("${tago.admin.member-ids:}") String adminMemberIds) {
        this.adminIds = Arrays.stream(adminMemberIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isPublic(String httpMethod, String path) {
        return PUBLIC.stream().anyMatch(r ->
                r.method().name().equalsIgnoreCase(httpMethod) && matcher.match(r.pattern(), path));
    }

    public boolean isAdminOnly(String path) {
        return matcher.match(ADMIN_PATTERN, path);
    }

    public boolean isAdmin(String memberId) {
        return memberId != null && adminIds.contains(memberId);
    }
}
