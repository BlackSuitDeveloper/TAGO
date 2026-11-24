package com.kedu.tago.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.kedu.tago.common.auth.RequireRole;
import com.kedu.tago.common.exception.GlobalExceptionHandler;
import com.kedu.tago.common.utils.JwtUtil;
import com.kedu.tago.interceptor.AuthInterceptor;
import com.kedu.tago.member.Role;
import com.kedu.tago.station.StationController;
import com.kedu.tago.station.StationService;
import com.kedu.tago.sync.config.TagoRouteConfig;
import com.kedu.tago.sync.controller.TagoSyncController;
import com.kedu.tago.sync.service.MasterSyncService;
import com.kedu.tago.sync.service.ScheduleSyncService;
import com.kedu.tago.wishlist.WishListController;
import com.kedu.tago.wishlist.WishListService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 인증/인가: AccessPolicy(경로 정책)와 AuthInterceptor(토큰 검증, 권한 확인) 검증 */
class AccessControlTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-0123";

    @Nested
    @DisplayName("AccessPolicy: 공개 경로 / 경로 기준 권한")
    class Policy {

        private final AccessPolicy policy = new AccessPolicy();

        @Test
        @DisplayName("공개는 메서드까지 일치해야 한다 (같은 경로라도 다른 메서드는 보호)")
        void publicRoutesAreMethodAware() {
            assertTrue(policy.isPublic("POST", "/member"));            // 회원가입
            assertFalse(policy.isPublic("DELETE", "/member"));         // 회원 탈퇴
            assertFalse(policy.isPublic("PUT", "/member"));
            assertTrue(policy.isPublic("POST", "/member/login"));
            assertTrue(policy.isPublic("GET", "/member/checkId"));
            assertFalse(policy.isPublic("GET", "/member/mypage"));
            assertTrue(policy.isPublic("POST", "/auth/find-id/send-code"));
            assertTrue(policy.isPublic("GET", "/station"));
            assertTrue(policy.isPublic("GET", "/schedule"));
            assertTrue(policy.isPublic("GET", "/schedule/12"));
            assertFalse(policy.isPublic("POST", "/schedule/12"));
            assertTrue(policy.isPublic("GET", "/seat"));
            assertTrue(policy.isPublic("GET", "/seat/stream"));
        }

        @Test
        @DisplayName("그 밖의 경로(새 API 포함)는 기본적으로 로그인 필요")
        void everythingElseIsProtected() {
            for (String path : new String[]{"/queue", "/queue/abc", "/reservation", "/reservation/me", "/reservation/3/cancel",
                    "/payment", "/wishlist", "/wishlist/5", "/anything/new"}) {
                assertFalse(policy.isPublic("GET", path), path);
                assertFalse(policy.isPublic("POST", path), path);
            }
        }

        @Test
        @DisplayName("/admin/** 은 경로만으로 ADMIN, 나머지는 USER")
        void requiredRoleByPath() {
            assertEquals(Role.ADMIN, policy.requiredRole("/admin/sync/mock"));
            assertEquals(Role.ADMIN, policy.requiredRole("/admin/anything"));
            assertEquals(Role.USER, policy.requiredRole("/member/mypage"));
            assertEquals(Role.USER, policy.requiredRole("/reservation"));
        }
    }

    /** /admin 경로가 아니어도 @RequireRole 만으로 권한이 걸리는지 확인하는 시험용 컨트롤러 */
    @RestController
    @RequestMapping("/secret")
    static class SecretController {
        @RequireRole(Role.ADMIN)
        @GetMapping("/admin-only")
        public String adminOnly() {
            return "ok";
        }

        @GetMapping("/any-member")
        public String anyMember() {
            return "ok";
        }
    }

    @RestController
    @RequireRole(Role.ADMIN)
    @RequestMapping("/vault")
    static class VaultController {
        @GetMapping("/a")
        public String a() {
            return "ok";
        }
    }

    @Nested
    @DisplayName("AuthInterceptor: 토큰 검증과 권한 확인")
    class Interceptor {

        private final JwtUtil jwt = new JwtUtil(3_600_000L, SECRET);
        private final MockMvc mvc = MockMvcBuilders
                .standaloneSetup(
                        new StationController(mock(StationService.class)),
                        new WishListController(mock(WishListService.class)),
                        new TagoSyncController(mock(MasterSyncService.class), mock(ScheduleSyncService.class), mock(TagoRouteConfig.class)),
                        new SecretController(),
                        new VaultController())
                .addInterceptors(new AuthInterceptor(jwt, new AccessPolicy()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        private String user() {
            return "Bearer " + jwt.createToken("user1", Role.USER);
        }

        private String admin() {
            return "Bearer " + jwt.createToken("admin1", Role.ADMIN);
        }

        /** ROLE 도입 전에 발급된 토큰 (role 클레임이 guest) */
        private String legacyGuestToken() {
            return "Bearer " + JWT.create().withSubject("old-user").withClaim("role", "guest")
                    .withExpiresAt(new Date(System.currentTimeMillis() + 3_600_000L)).sign(Algorithm.HMAC256(SECRET));
        }

        @Test
        @DisplayName("공개 경로는 토큰 없이 통과")
        void publicWithoutToken() throws Exception {
            mvc.perform(get("/station")).andExpect(status().isOk());
        }

        @Test
        @DisplayName("보호 경로: 토큰 없음 -> 401 / 2000")
        void protectedWithoutToken() throws Exception {
            mvc.perform(get("/wishlist"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value(2000))
                    .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
        }

        @Test
        @DisplayName("보호 경로: 잘못된 토큰 -> 401 / 2001")
        void protectedWithBadToken() throws Exception {
            mvc.perform(get("/wishlist").header("Authorization", "Bearer not-a-jwt"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(2001));
        }

        @Test
        @DisplayName("일반 API: USER 도 ADMIN 도 사용 가능")
        void userApisAreOpenToBothRoles() throws Exception {
            mvc.perform(get("/wishlist").header("Authorization", user())).andExpect(status().isOk());
            mvc.perform(get("/wishlist").header("Authorization", admin())).andExpect(status().isOk());
        }

        @Test
        @DisplayName("/admin/**: 토큰 없음 401, USER 403 / 2004, ADMIN 통과")
        void adminRoutes() throws Exception {
            mvc.perform(get("/admin/sync/status"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(2000));

            mvc.perform(get("/admin/sync/status").header("Authorization", user()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value(2004));

            mvc.perform(get("/admin/sync/status").header("Authorization", admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("@RequireRole 은 경로와 상관없이 적용된다 (메서드 단위)")
        void requireRoleOnMethod() throws Exception {
            mvc.perform(get("/secret/admin-only").header("Authorization", user()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(2004));
            mvc.perform(get("/secret/admin-only").header("Authorization", admin())).andExpect(status().isOk());

            // 같은 컨트롤러의 어노테이션 없는 API 는 로그인한 회원이면 가능
            mvc.perform(get("/secret/any-member").header("Authorization", user())).andExpect(status().isOk());
        }

        @Test
        @DisplayName("@RequireRole 은 클래스에 붙이면 컨트롤러 전체에 적용된다")
        void requireRoleOnClass() throws Exception {
            mvc.perform(get("/vault/a").header("Authorization", user())).andExpect(status().isForbidden());
            mvc.perform(get("/vault/a").header("Authorization", admin())).andExpect(status().isOk());
        }

        @Test
        @DisplayName("ROLE 도입 전에 발급된 토큰(role=guest)은 USER 로 취급")
        void legacyTokenIsUser() throws Exception {
            mvc.perform(get("/wishlist").header("Authorization", legacyGuestToken())).andExpect(status().isOk());
            mvc.perform(get("/admin/sync/status").header("Authorization", legacyGuestToken()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(2004));
        }
    }
}
