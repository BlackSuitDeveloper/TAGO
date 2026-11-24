package com.kedu.tago.member;

import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.member.dto.MemberSignupRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemberValidatorTest {

    private MemberValidator validator;

    @BeforeEach
    void setUp() {
        validator = new MemberValidator();
    }

    private MemberSignupRequestDTO createValidDto() {
        return MemberSignupRequestDTO.builder()
                .id("testuser1")
                .pw("Password123!")
                .name("홍길동")
                .email("user@example.com")
                .phone("010-1234-5678")
                .signupToken("valid-token")
                .zipcode("12345")
                .address1("서울시 강남구")
                .address2("101호")
                .build();
    }

    @Test
    @DisplayName("정상 입력값 검증 통과")
    void validateSignup_validInput_passes() {
        MemberSignupRequestDTO dto = createValidDto();
        assertDoesNotThrow(() -> validator.validateSignup(dto));
        assertEquals("01012345678", dto.getPhone());
    }

    @Test
    @DisplayName("아이디가 4자 미만이면 예외 발생")
    void validateSignup_shortId_throwsException() {
        MemberSignupRequestDTO dto = createValidDto();
        dto.setId("abc");

        ApiException ex = assertThrows(ApiException.class, () -> validator.validateSignup(dto));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("아이디는 4~20자의 영문 및 숫자"));
    }

    @Test
    @DisplayName("아이디에 특수문자나 공백이 포함되면 예외 발생")
    void validateSignup_invalidCharId_throwsException() {
        MemberSignupRequestDTO dto = createValidDto();
        dto.setId("user!@#");

        ApiException ex = assertThrows(ApiException.class, () -> validator.validateSignup(dto));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("비밀번호가 8자 미만이면 예외 발생")
    void validateSignup_shortPassword_throwsException() {
        MemberSignupRequestDTO dto = createValidDto();
        dto.setPw("Pass1!");

        ApiException ex = assertThrows(ApiException.class, () -> validator.validateSignup(dto));
        assertEquals(ErrorCode.PASSWORD_TOO_SHORT, ex.getErrorCode());
    }

    @Test
    @DisplayName("비밀번호에 영문/숫자/특수문자 중 하나라도 누락되면 예외 발생")
    void validateSignup_simplePassword_throwsException() {
        MemberSignupRequestDTO dto = createValidDto();
        dto.setPw("password1234"); // 특수문자 누락

        ApiException ex = assertThrows(ApiException.class, () -> validator.validateSignup(dto));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("특수문자"));
    }

    @Test
    @DisplayName("이메일 형식이 올바르지 않으면 예외 발생")
    void validateSignup_invalidEmail_throwsException() {
        MemberSignupRequestDTO dto = createValidDto();
        dto.setEmail("invalid-email-address");

        ApiException ex = assertThrows(ApiException.class, () -> validator.validateSignup(dto));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("올바른 이메일 형식"));
    }

    @Test
    @DisplayName("전화번호 형식이 올바르지 않으면 예외 발생")
    void validateSignup_invalidPhone_throwsException() {
        MemberSignupRequestDTO dto = createValidDto();
        dto.setPhone("02-123-4567"); // 010 번호가 아님

        ApiException ex = assertThrows(ApiException.class, () -> validator.validateSignup(dto));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("휴대전화 번호"));
    }

    @Test
    @DisplayName("이메일 인증 토큰이 누락되면 EMAIL_VERIFICATION_REQUIRED 예외 발생")
    void validateSignup_missingToken_throwsException() {
        MemberSignupRequestDTO dto = createValidDto();
        dto.setSignupToken("");

        ApiException ex = assertThrows(ApiException.class, () -> validator.validateSignup(dto));
        assertEquals(ErrorCode.EMAIL_VERIFICATION_REQUIRED, ex.getErrorCode());
    }
}
