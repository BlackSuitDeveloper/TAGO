package com.kedu.tago.auth;

import com.kedu.tago.auth.dto.AuthLoginRequestDTO;
import com.kedu.tago.auth.dto.ResetPasswordRequestDTO;
import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.common.mail.MailService;
import com.kedu.tago.common.security.JwtUtil;
import com.kedu.tago.member.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetSecurityTest {

    @Mock
    private MailService mailService;

    @Mock
    private MemberService memberService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private LoginAttemptService loginAttemptService;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(mailService, memberService, jwtUtil, loginAttemptService);
    }

    @Test
    @DisplayName("SEC-1 방어: 공격자 토큰으로 피해자(victim)의 비밀번호 변경 시도 시 거부되어야 한다")
    void resetPassword_withTamperedId_isForbidden() {
        // 공격자가 자신의 계정(attackerId)으로 발급받은 resetToken
        String attackerToken = "attacker-reset-token-uuid";
        when(mailService.validateAndConsumeResetToken(attackerToken)).thenReturn("attackerId");

        // 공격자가 요청 본문에는 피해자의 아이디(victimId)를 넣어서 전송
        ResetPasswordRequestDTO dto = new ResetPasswordRequestDTO();
        dto.setId("victimId");
        dto.setEmail("attacker@email.com");
        dto.setResetToken(attackerToken);
        dto.setNewPassword("hackedPassword123!");

        ApiException ex = assertThrows(ApiException.class, () -> authController.resetPassword(dto));

        assertEquals(ErrorCode.NOT_RESOURCE_OWNER, ex.getErrorCode());
        // 피해자(victimId)의 비밀번호는 절대 변경되지 않아야 함
        verify(memberService, never()).updatePassword(eq("victimId"), anyString());
    }

    @Test
    @DisplayName("정상 흐름: 본인 토큰으로 비밀번호 변경 시 성공 및 잠금 해제")
    void resetPassword_withValidToken_succeeds() {
        String validToken = "valid-token-uuid";
        when(mailService.validateAndConsumeResetToken(validToken)).thenReturn("user1");
        when(memberService.updatePassword("user1", "newPassword123!")).thenReturn(true);

        ResetPasswordRequestDTO dto = new ResetPasswordRequestDTO();
        dto.setId("user1");
        dto.setEmail("user1@email.com");
        dto.setResetToken(validToken);
        dto.setNewPassword("newPassword123!");

        var response = authController.resetPassword(dto);

        assertTrue(response.isSuccess());
        verify(memberService).updatePassword("user1", "newPassword123!");
        verify(loginAttemptService).unlockAccount("user1");
    }

    @Test
    @DisplayName("만료된 토큰으로 재설정 시도 시 VERIFICATION_EXPIRED 예외 발생")
    void resetPassword_withExpiredToken_throwsExpired() {
        String expiredToken = "expired-token-uuid";
        when(mailService.validateAndConsumeResetToken(expiredToken)).thenReturn(null);

        ResetPasswordRequestDTO dto = new ResetPasswordRequestDTO();
        dto.setId("user1");
        dto.setEmail("user1@email.com");
        dto.setResetToken(expiredToken);
        dto.setNewPassword("newPassword123!");

        ApiException ex = assertThrows(ApiException.class, () -> authController.resetPassword(dto));
        assertEquals(ErrorCode.VERIFICATION_EXPIRED, ex.getErrorCode());
        verify(memberService, never()).updatePassword(anyString(), anyString());
    }

    @Test
    @DisplayName("로그인 요청 시 아이디가 누락되면 BAD_REQUEST 예외 발생")
    void login_missingId_throwsBadRequest() {
        AuthLoginRequestDTO dto = new AuthLoginRequestDTO("", "password123!");
        MockHttpServletRequest request = new MockHttpServletRequest();

        ApiException ex = assertThrows(ApiException.class, () -> authController.login(dto, request));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("아이디를 입력해 주세요"));
    }

    @Test
    @DisplayName("로그인 요청 시 비밀번호가 누락되면 BAD_REQUEST 예외 발생")
    void login_missingPassword_throwsBadRequest() {
        AuthLoginRequestDTO dto = new AuthLoginRequestDTO("validUser", "");
        MockHttpServletRequest request = new MockHttpServletRequest();

        ApiException ex = assertThrows(ApiException.class, () -> authController.login(dto, request));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("비밀번호를 입력해 주세요"));
    }
}
