package com.kedu.tago.common.mail;

import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.config.RedisConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignupEmailVerificationTest {

    @Mock
    private RedisConfig redisConfig;

    @Mock
    private JavaMailSender mailSender;

    private MailService mailService;

    @BeforeEach
    void setUp() {
        mailService = new MailService(redisConfig, mailSender);
        ReflectionTestUtils.setField(mailService, "senderEmail", "noreply@tago.com");
    }

    @Test
    @DisplayName("메일 발송 실패 시 MAIL_SEND_FAILED 예외 발생 및 Redis에 코드 미저장")
    void sendSignupVerificationCode_whenMailFails_throwsException() {
        when(redisConfig.hasKey("VERIFY:SIGNUP:COOLDOWN:test@example.com")).thenReturn(false);
        doThrow(new RuntimeException("SMTP Connection Error")).when(mailSender).send(any(SimpleMailMessage.class));

        ApiException ex = assertThrows(ApiException.class, () ->
                mailService.sendSignupVerificationCode("test@example.com"));

        assertEquals(ErrorCode.MAIL_SEND_FAILED, ex.getErrorCode());
        // 메일 발송이 실패했으므로 Redis에 코드가 저장되지 않아야 함
        verify(redisConfig, never()).setValue(eq("VERIFY:SIGNUP:CODE:test@example.com"), anyString(), anyLong());
    }

    @Test
    @DisplayName("재발송 쿨다운(60초) 내 재요청 시 EMAIL_SEND_COOLDOWN 예외 발생")
    void sendSignupVerificationCode_inCooldown_throwsException() {
        when(redisConfig.hasKey("VERIFY:SIGNUP:COOLDOWN:test@example.com")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () ->
                mailService.sendSignupVerificationCode("test@example.com"));

        assertEquals(ErrorCode.EMAIL_SEND_COOLDOWN, ex.getErrorCode());
    }

    @Test
    @DisplayName("인증번호 발송 시 메일 발송 및 Redis에 코드, 쿨다운 설정")
    void sendSignupVerificationCode_success() {
        when(redisConfig.hasKey("VERIFY:SIGNUP:COOLDOWN:test@example.com")).thenReturn(false);

        mailService.sendSignupVerificationCode("test@example.com");

        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(redisConfig).setValue(eq("VERIFY:SIGNUP:CODE:test@example.com"), anyString(), eq(5 * 60 * 1000L));
        verify(redisConfig).setValue(eq("VERIFY:SIGNUP:COOLDOWN:test@example.com"), eq("1"), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("인증번호가 일치하면 signupToken 발급")
    void verifySignupVerificationCode_match_returnsToken() {
        when(redisConfig.getValue("VERIFY:SIGNUP:CODE:test@example.com")).thenReturn("123456");
        when(redisConfig.increment("VERIFY:SIGNUP:FAIL:test@example.com")).thenReturn(1L);

        String token = mailService.verifySignupVerificationCode("test@example.com", "123456");

        assertNotNull(token);
        verify(redisConfig).deleteValue("VERIFY:SIGNUP:CODE:test@example.com");
        verify(redisConfig).setValue(startsWith("TOKEN:SIGNUP:"), eq("test@example.com"), eq(15 * 60 * 1000L));
    }

    @Test
    @DisplayName("인증번호가 불일치하면 시도 횟수 안내 및 CODE_MISMATCH 예외 발생")
    void verifySignupVerificationCode_mismatch_throwsException() {
        when(redisConfig.getValue("VERIFY:SIGNUP:CODE:test@example.com")).thenReturn("123456");
        when(redisConfig.increment("VERIFY:SIGNUP:FAIL:test@example.com")).thenReturn(2L);

        ApiException ex = assertThrows(ApiException.class, () ->
                mailService.verifySignupVerificationCode("test@example.com", "999999"));

        assertEquals(ErrorCode.CODE_MISMATCH, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("남은 시도: 3회"));
    }

    @Test
    @DisplayName("인증 시도 5회 초과 시 코드 파기 및 VERIFICATION_ATTEMPTS_EXCEEDED 예외 발생")
    void verifySignupVerificationCode_exceedsAttempts_invalidatesCode() {
        when(redisConfig.getValue("VERIFY:SIGNUP:CODE:test@example.com")).thenReturn("123456");
        when(redisConfig.increment("VERIFY:SIGNUP:FAIL:test@example.com")).thenReturn(6L);

        ApiException ex = assertThrows(ApiException.class, () ->
                mailService.verifySignupVerificationCode("test@example.com", "999999"));

        assertEquals(ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED, ex.getErrorCode());
        verify(redisConfig).deleteValue("VERIFY:SIGNUP:CODE:test@example.com");
    }

    @Test
    @DisplayName("signupToken 검증 및 1회성 소모 확인")
    void validateAndConsumeSignupToken_valid_consumesToken() {
        when(redisConfig.getValue("TOKEN:SIGNUP:valid-token")).thenReturn("test@example.com");

        boolean valid = mailService.validateAndConsumeSignupToken("valid-token", "test@example.com");

        assertTrue(valid);
        verify(redisConfig).deleteValue("TOKEN:SIGNUP:valid-token");
    }

    @Test
    @DisplayName("비밀번호 재설정 인증 성공 시 회원 아이디가 바인딩된 토큰 발급")
    void verifyResetPasswordCode_bindsMemberIdToToken() {
        when(redisConfig.getValue("VERIFY:RESET_PW:CODE:user1:user1@example.com")).thenReturn("654321");
        when(redisConfig.increment("VERIFY:RESET_PW:FAIL:user1:user1@example.com")).thenReturn(1L);

        String resetToken = mailService.verifyResetPasswordCode("user1", "user1@example.com", "654321");

        assertNotNull(resetToken);
        verify(redisConfig).deleteValue("VERIFY:RESET_PW:CODE:user1:user1@example.com");
        // 핵심: 토큰에 회원 아이디가 저장됨
        verify(redisConfig).setValue(startsWith("TOKEN:RESET_PW:"), eq("user1"), eq(10 * 60 * 1000L));
    }

    @Test
    @DisplayName("비밀번호 재설정 토큰 검증 시 회원 아이디 반환 및 1회성 소모")
    void validateAndConsumeResetToken_returnsBoundMemberId() {
        when(redisConfig.getValue("TOKEN:RESET_PW:valid-reset-token")).thenReturn("user1");

        String boundId = mailService.validateAndConsumeResetToken("valid-reset-token");

        assertEquals("user1", boundId);
        verify(redisConfig).deleteValue("TOKEN:RESET_PW:valid-reset-token");
    }
}
