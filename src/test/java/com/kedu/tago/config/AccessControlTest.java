package com.kedu.tago.config;

import com.kedu.tago.common.exception.GlobalExceptionHandler;
import com.kedu.tago.common.utils.JwtUtil;
import com.kedu.tago.interceptor.AuthInterceptor;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 인증/인가: AccessPolicy(경로 정책)와 AuthInterceptor(토큰 검증, 관리자 전용) 검증 */
class AccessControlTest {

    @Nested
    @DisplayName("AccessPolicy: 공개 / 관리자 전용 경로")
    class Policy {

        private final AccessPolicy policy = new AccessPolicy("admin1, admin2");

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
        @DisplayName("/admin/** 는 관리자 전용, 관리자는 설정한 아이디만")
        void adminOnly() {
            assertTrue(policy.isAdminOnly("/admin/sync/mock"));
            assertFalse(policy.isAdminOnly("/member/mypage"));
            assertTrue(policy.isAdmin("admin1"));
            assertTrue(policy.isAdmin("admin2"));
            assertFalse(policy.isAdmin("user1"));
            assertFalse(policy.isAdmin(null));
        }

        @Test
        @DisplayName("관리자 설정이 비어 있으면 관리자가 없다")
        void noAdminsByDefault() {
            assertFalse(new AccessPolicy("").isAdmin("admin1"));
            assertFalse(new AccessPolicy("  , ").isAdmin(""));
        }
    }

    @Nested
    @DisplayName("AuthInterceptor: 요청 처리")
    class Interceptor {

        private final JwtUtil jwt = new JwtUtil(3_600_000L, "test-secret-test-secret-test-secret-0123");
        private final MockMvc mvc = MockMvcBuilders
                .standaloneSetup(
                        new StationController(mock(StationService.class)),
                        new WishListController(mock(WishListService.class)),
                        new TagoSyncController(mock(MasterSyncService.class), mock(ScheduleSyncService.class), mock(TagoRouteConfig.class)))
                .addInterceptors(new AuthInterceptor(jwt, new AccessPolicy("admin1")))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        private String bearer(String id) {
            return "Bearer " + jwt.createToken(id);
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
        @DisplayName("보호 경로: 유효한 토큰 -> 통과, 아이디가 컨트롤러로 전달")
        void protectedWithValidToken() throws Exception {
            mvc.perform(get("/wishlist").header("Authorization", bearer("user1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("/admin/**: 토큰 없음 401, 일반 회원 403 / 2004, 관리자는 통과")
        void adminRoutes() throws Exception {
            mvc.perform(get("/admin/sync/status"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(2000));

            mvc.perform(get("/admin/sync/status").header("Authorization", bearer("user1")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.code").value(2004));

            mvc.perform(get("/admin/sync/status").header("Authorization", bearer("admin1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}
