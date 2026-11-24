package com.kedu.tago.auth;

import com.kedu.tago.auth.dto.AuthLoginRequestDTO;
import com.kedu.tago.auth.dto.FindIdRequestDTO;
import com.kedu.tago.auth.dto.ResetPasswordRequestDTO;
import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.common.mail.MailService;
import com.kedu.tago.common.response.ResWrapper;
import com.kedu.tago.common.security.JwtUtil;
import com.kedu.tago.member.MemberService;
import com.kedu.tago.member.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 로그인, 아이디 찾기, 비밀번호 재설정 API (로그인 전 계정 접근) */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MailService mailService;
    private final MemberService memberService;
    private final JwtUtil jwtUtil;

    /** 로그인 및 토큰 발급 (POST /api/member/login) */
    @PostMapping("/login")
    public ResWrapper<Map<String, String>> login(@RequestBody AuthLoginRequestDTO dto) {
        if (!memberService.isMember(dto.getId(), dto.getPw())) {
            throw new ApiException(ErrorCode.LOGIN_FAILED);
        }
        Role role = memberService.getRole(dto.getId());
        String token = jwtUtil.createToken(dto.getId(), role);
        return ResWrapper.ok(Map.of("token", token, "id", dto.getId(), "role", role.name()));
    }

    /** 아이디 찾기 인증번호 발송 (POST /api/auth/find-id/send-code) */
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

    /** 아이디 찾기 인증번호 확인 및 아이디 반환 (POST /api/auth/find-id/verify) */
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

    /** 비밀번호 재설정 인증번호 발송 (POST /api/auth/reset-password/send-code) */
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

        mailService.sendResetPasswordCode(dto.getEmail().trim());
        return ResWrapper.done("인증번호가 가입된 이메일로 발송되었습니다. (5분 유효)");
    }

    /** 비밀번호 재설정 인증번호 확인 및 재설정 토큰 발급 (POST /api/auth/reset-password/verify) */
    @PostMapping("/reset-password/verify")
    public ResWrapper<Map<String, String>> verifyResetPwCode(@RequestBody ResetPasswordRequestDTO dto) {
        if (dto.getEmail() == null || dto.getCode() == null) {
            throw new ApiException(ErrorCode.EMAIL_CODE_REQUIRED);
        }

        String resetToken = mailService.verifyResetPasswordCode(dto.getEmail().trim(), dto.getCode().trim());
        if (resetToken == null) {
            throw new ApiException(ErrorCode.CODE_MISMATCH);
        }

        return ResWrapper.ok(Map.of("resetToken", resetToken), "이메일 인증이 완료되었습니다.");
    }

    /** 새 비밀번호 재설정 (POST /api/auth/reset-password) */
    @PostMapping("/reset-password")
    public ResWrapper<Void> resetPassword(@RequestBody ResetPasswordRequestDTO dto) {
        if (dto.getId() == null || dto.getEmail() == null ||
            dto.getResetToken() == null || dto.getNewPassword() == null) {
            throw new ApiException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (dto.getNewPassword().length() < 8) {
            throw new ApiException(ErrorCode.PASSWORD_TOO_SHORT);
        }

        boolean isTokenValid = mailService.validateAndConsumeResetToken(dto.getResetToken().trim(), dto.getEmail().trim());
        if (!isTokenValid) {
            throw new ApiException(ErrorCode.VERIFICATION_EXPIRED);
        }

        boolean updated = memberService.updatePassword(dto.getId().trim(), dto.getNewPassword());
        if (!updated) {
            throw new ApiException(ErrorCode.PASSWORD_CHANGE_FAILED);
        }

        return ResWrapper.done("비밀번호가 성공적으로 변경되었습니다. 새로운 비밀번호로 로그인해 주세요.");
    }
}
