package com.kedu.tago.auth;

import com.kedu.tago.auth.dto.AuthLoginRequestDTO;
import com.kedu.tago.auth.dto.FindIdRequestDTO;
import com.kedu.tago.auth.dto.ResetPasswordRequestDTO;
import com.kedu.tago.auth.dto.SignupEmailRequestDTO;
import com.kedu.tago.common.ApiPaths;
import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.common.mail.MailService;
import com.kedu.tago.common.response.ResWrapper;
import com.kedu.tago.common.security.JwtUtil;
import com.kedu.tago.member.MemberService;
import com.kedu.tago.member.Role;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 로그인, 아이디 찾기, 비밀번호 재설정, 회원가입 이메일 인증 API */
@RestController
@RequestMapping(ApiPaths.V1 + "/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MailService mailService;
    private final MemberService memberService;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;

    /** 로그인 및 토큰 발급 (POST /api/v1/auth/login) - 입력값 사전 검증 및 계정/IP 잠금 적용 */
    @PostMapping("/login")
    public ResWrapper<Map<String, String>> login(@RequestBody AuthLoginRequestDTO dto, HttpServletRequest request) {
        if (dto == null || dto.getId() == null || dto.getId().trim().isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "아이디를 입력해 주세요.");
        }
        if (dto.getPw() == null || dto.getPw().isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "비밀번호를 입력해 주세요.");
        }

        String memberId = dto.getId().trim();

        // 아이디 기본 형식(4~20자 영문/숫자) 검증 (비정상 입력 시 IP 실패 카운트 증가 후 차단)
        if (!memberId.matches("^[a-zA-Z0-9]{4,20}$")) {
            loginAttemptService.processLoginFailure(null, request);
        }

        loginAttemptService.checkLoginLock(memberId, request);

        if (!memberService.isMember(memberId, dto.getPw())) {
            loginAttemptService.processLoginFailure(memberId, request);
        }

        loginAttemptService.processLoginSuccess(memberId, request);
        Role role = memberService.getRole(memberId);
        String token = jwtUtil.createToken(memberId, role);
        return ResWrapper.ok(Map.of("token", token, "id", memberId, "role", role.name()));
    }

    /** 회원가입 이메일 인증번호 발송 (POST /api/v1/auth/signup/send-code) */
    @PostMapping("/signup/send-code")
    public ResWrapper<Void> sendSignupVerificationCode(@RequestBody SignupEmailRequestDTO dto) {
        if (dto.getEmail() == null || dto.getEmail().trim().isEmpty()) {
            throw new ApiException(ErrorCode.MISSING_REQUIRED_FIELD, "이메일을 입력해 주세요.");
        }
        String email = dto.getEmail().trim();
        if (memberService.checkEmail(email) > 0) {
            throw new ApiException(ErrorCode.DUPLICATE_EMAIL);
        }

        mailService.sendSignupVerificationCode(email);
        return ResWrapper.done("인증번호가 입력하신 이메일로 발송되었습니다. (5분 유효)");
    }

    /** 회원가입 이메일 인증번호 확인 및 가입 토큰 발급 (POST /api/v1/auth/signup/verify) */
    @PostMapping("/signup/verify")
    public ResWrapper<Map<String, String>> verifySignupCode(@RequestBody SignupEmailRequestDTO dto) {
        if (dto.getEmail() == null || dto.getCode() == null ||
            dto.getEmail().trim().isEmpty() || dto.getCode().trim().isEmpty()) {
            throw new ApiException(ErrorCode.EMAIL_CODE_REQUIRED);
        }

        String signupToken = mailService.verifySignupVerificationCode(dto.getEmail().trim(), dto.getCode().trim());
        return ResWrapper.ok(Map.of("signupToken", signupToken, "email", dto.getEmail().trim()), "이메일 인증이 완료되었습니다.");
    }

    /** 아이디 찾기 인증번호 발송 (POST /api/v1/auth/find-id/send-code) */
    @PostMapping("/find-id/send-code")
    public ResWrapper<Void> sendFindIdCode(@RequestBody FindIdRequestDTO dto) {
        if (dto.getName() == null || dto.getName().trim().isEmpty() ||
            dto.getEmail() == null || dto.getEmail().trim().isEmpty()) {
            throw new ApiException(ErrorCode.NAME_EMAIL_REQUIRED);
        }

        String memberId = memberService.findIdByNameAndEmail(dto.getName().trim(), dto.getEmail().trim());
        if (memberId == null) {
            throw new ApiException(ErrorCode.MEMBER_INFO_MISMATCH);
        }

        mailService.sendFindIdCode(dto.getEmail().trim());
        return ResWrapper.done("인증번호가 가입된 이메일로 발송되었습니다. (5분 유효)");
    }

    /** 아이디 찾기 인증번호 확인 및 아이디 반환 (POST /api/v1/auth/find-id/verify) */
    @PostMapping("/find-id/verify")
    public ResWrapper<Map<String, String>> verifyFindIdCode(@RequestBody FindIdRequestDTO dto) {
        if (dto.getEmail() == null || dto.getCode() == null) {
            throw new ApiException(ErrorCode.EMAIL_CODE_REQUIRED);
        }

        boolean isValid = mailService.verifyFindIdCode(dto.getEmail().trim(), dto.getCode().trim());
        if (!isValid) {
            throw new ApiException(ErrorCode.CODE_MISMATCH);
        }

        String memberId = memberService.findIdByNameAndEmail(dto.getName().trim(), dto.getEmail().trim());
        if (memberId == null) {
            throw new ApiException(ErrorCode.MEMBER_NOT_FOUND);
        }

        // 아이디 마스킹 (admin123 -> adm*****)
        String maskedId;
        if (memberId.length() <= 3) {
            maskedId = memberId.charAt(0) + "*".repeat(memberId.length() - 1);
        } else {
            maskedId = memberId.substring(0, 3) + "*".repeat(memberId.length() - 3);
        }

        return ResWrapper.ok(Map.of("id", memberId, "maskedId", maskedId), "인증이 완료되었습니다.");
    }

    /** 비밀번호 재설정 인증번호 발송 (POST /api/v1/auth/reset-password/send-code) */
    @PostMapping("/reset-password/send-code")
    public ResWrapper<Void> sendResetPwCode(@RequestBody ResetPasswordRequestDTO dto) {
        if (dto.getId() == null || dto.getId().trim().isEmpty() ||
            dto.getEmail() == null || dto.getEmail().trim().isEmpty()) {
            throw new ApiException(ErrorCode.ID_EMAIL_REQUIRED);
        }

        boolean exists = memberService.validateMemberByIdAndEmail(dto.getId().trim(), dto.getEmail().trim());
        if (!exists) {
            throw new ApiException(ErrorCode.MEMBER_INFO_MISMATCH, "입력하신 아이디와 이메일이 일치하는 회원을 찾을 수 없습니다.");
        }

        mailService.sendResetPasswordCode(dto.getId().trim(), dto.getEmail().trim());
        return ResWrapper.done("인증번호가 가입된 이메일로 발송되었습니다. (5분 유효)");
    }

    /** 비밀번호 재설정 인증번호 확인 및 재설정 토큰 발급 (POST /api/v1/auth/reset-password/verify) */
    @PostMapping("/reset-password/verify")
    public ResWrapper<Map<String, String>> verifyResetPwCode(@RequestBody ResetPasswordRequestDTO dto) {
        if (dto.getId() == null || dto.getId().trim().isEmpty() ||
            dto.getEmail() == null || dto.getEmail().trim().isEmpty() ||
            dto.getCode() == null || dto.getCode().trim().isEmpty()) {
            throw new ApiException(ErrorCode.MISSING_REQUIRED_FIELD, "아이디, 이메일, 인증번호를 모두 입력해 주세요.");
        }

        String resetToken = mailService.verifyResetPasswordCode(dto.getId().trim(), dto.getEmail().trim(), dto.getCode().trim());
        return ResWrapper.ok(Map.of("resetToken", resetToken), "이메일 인증이 완료되었습니다.");
    }

    /** 새 비밀번호 재설정 (POST /api/v1/auth/reset-password) */
    @PostMapping("/reset-password")
    public ResWrapper<Void> resetPassword(@RequestBody ResetPasswordRequestDTO dto) {
        if (dto.getResetToken() == null || dto.getResetToken().trim().isEmpty() ||
            dto.getNewPassword() == null) {
            throw new ApiException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (dto.getNewPassword().length() < 8) {
            throw new ApiException(ErrorCode.PASSWORD_TOO_SHORT);
        }

        // 토큰에 바인딩된 회원 아이디 조회 및 1회성 소모
        String verifiedMemberId = mailService.validateAndConsumeResetToken(dto.getResetToken().trim());
        if (verifiedMemberId == null) {
            throw new ApiException(ErrorCode.VERIFICATION_EXPIRED);
        }

        // 요청 본문에 id가 제공된 경우, 토큰의 회원 아이디와 일치하는지 교차 검증 (조작 시도 차단)
        if (dto.getId() != null && !dto.getId().trim().isEmpty()) {
            if (!verifiedMemberId.equalsIgnoreCase(dto.getId().trim())) {
                throw new ApiException(ErrorCode.NOT_RESOURCE_OWNER, "인증된 계정 정보와 일치하지 않습니다.");
            }
        }

        // 본문의 id 대신 검증된 verifiedMemberId로만 비밀번호 변경
        boolean updated = memberService.updatePassword(verifiedMemberId, dto.getNewPassword());
        if (!updated) {
            throw new ApiException(ErrorCode.PASSWORD_CHANGE_FAILED);
        }

        // 비밀번호 재설정 성공 시 계정 잠금 해제
        loginAttemptService.unlockAccount(verifiedMemberId);

        return ResWrapper.done("비밀번호가 성공적으로 변경되었습니다. 새로운 비밀번호로 로그인해 주세요.");
    }
}
