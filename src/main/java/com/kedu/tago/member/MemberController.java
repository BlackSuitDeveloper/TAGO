package com.kedu.tago.member;

import com.kedu.tago.common.ApiPaths;
import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.common.mail.MailService;
import com.kedu.tago.common.response.ResWrapper;
import com.kedu.tago.member.dto.MemberProfileResponseDTO;
import com.kedu.tago.member.dto.MemberSignupRequestDTO;
import com.kedu.tago.member.dto.MemberUpdateRequestDTO;
import com.kedu.tago.member.dto.MypagePasswordRequestDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 회원 가입·정보 관리·비밀번호 변경 API */
@RestController
@RequestMapping(ApiPaths.V1 + "/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final MailService mailService;
    private final MemberValidator memberValidator;
    private final com.kedu.tago.common.security.TokenManagementService tokenManagementService;

    /** 회원가입 (POST /api/v1/member) - EXC-1 정밀 유효성 검증 적용 */
    @PostMapping
    public ResWrapper<Void> signup(@RequestBody MemberSignupRequestDTO dto) {
        // 1. 아이디, 비밀번호, 이름, 이메일, 전화번호, 주소 정밀 검증
        memberValidator.validateSignup(dto);

        // 2. 이메일 인증 토큰 검증 및 1회성 소멸
        boolean verified = mailService.validateAndConsumeSignupToken(dto.getSignupToken(), dto.getEmail());
        if (!verified) {
            throw new ApiException(ErrorCode.EMAIL_VERIFICATION_REQUIRED);
        }

        // 3. 회원 정보 저장
        if (memberService.insertMember(dto) != 1) {
            throw new ApiException(ErrorCode.SIGNUP_FAILED);
        }
        return ResWrapper.done("회원가입이 완료되었습니다.");
    }

    /** 아이디 사용 가능 여부 (GET /api/v1/member/checkId) */
    @GetMapping("/checkId")
    public ResWrapper<Boolean> checkId(@RequestParam String id) {
        return ResWrapper.ok(memberService.checkId(id) == 0);
    }

    /** 내 정보 조회 (GET /api/v1/member/mypage) */
    @GetMapping("/mypage")
    public ResWrapper<MemberProfileResponseDTO> mypage(HttpServletRequest request) {
        String id = (String) request.getAttribute("id");
        return ResWrapper.ok(memberService.selectMember(id));
    }

    /** 내 정보 수정 (PUT /api/v1/member) */
    @PutMapping
    public ResWrapper<Void> updateMember(@RequestBody MemberUpdateRequestDTO dto, HttpServletRequest request) {
        String id = (String) request.getAttribute("id");
        dto.setId(id);
        memberService.updateMember(dto);
        return ResWrapper.done("수정 완료");
    }

    /** 회원 탈퇴 (DELETE /api/v1/member) */
    @DeleteMapping
    public ResWrapper<Void> deleteId(HttpServletRequest request) {
        String id = (String) request.getAttribute("id");
        memberService.deleteId(id);

        // 현재 토큰 및 해당 계정의 모든 토큰 무효화
        tokenManagementService.invalidateMemberTokens(id);
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            tokenManagementService.blacklistToken(authHeader.substring(7).trim());
        }

        return ResWrapper.done("탈퇴 완료");
    }

    /** 비밀번호 변경용 이메일 인증번호 발송 (POST /api/v1/member/password/send-code) */
    @PostMapping("/password/send-code")
    public ResWrapper<Map<String, String>> sendMypagePasswordCode(HttpServletRequest request) {
        String id = (String) request.getAttribute("id");
        MemberProfileResponseDTO profile = memberService.selectMember(id);

        if (profile == null || profile.getEmail() == null || profile.getEmail().trim().isEmpty()) {
            throw new ApiException(ErrorCode.EMAIL_NOT_REGISTERED);
        }

        mailService.sendMypagePasswordCode(id, profile.getEmail().trim());
        return ResWrapper.ok(
                Map.of("email", profile.getEmail()),
                "회원님의 가입 이메일(" + profile.getEmail() + ")로 인증번호가 발송되었습니다.");
    }

    /** 비밀번호 변경용 이메일 인증번호 확인 (POST /api/v1/member/password/verify) */
    @PostMapping("/password/verify")
    public ResWrapper<Void> verifyMypagePasswordCode(@RequestBody MypagePasswordRequestDTO dto, HttpServletRequest request) {
        String id = (String) request.getAttribute("id");
        MemberProfileResponseDTO profile = memberService.selectMember(id);

        if (profile == null || profile.getEmail() == null) {
            throw new ApiException(ErrorCode.MEMBER_NOT_FOUND);
        }
        if (dto.getCode() == null || dto.getCode().trim().isEmpty()) {
            throw new ApiException(ErrorCode.CODE_REQUIRED);
        }

        boolean isValid = mailService.verifyMypagePasswordCode(id, dto.getCode().trim());
        if (!isValid) {
            throw new ApiException(ErrorCode.CODE_MISMATCH);
        }
        return ResWrapper.done("이메일 본인인증이 완료되었습니다.");
    }

    /** 비밀번호 변경 (PUT /api/v1/member/password) */
    @PutMapping("/password")
    public ResWrapper<Void> updateMypagePassword(@RequestBody MypagePasswordRequestDTO dto, HttpServletRequest request) {
        String id = (String) request.getAttribute("id");
        MemberProfileResponseDTO profile = memberService.selectMember(id);

        if (profile == null || profile.getEmail() == null) {
            throw new ApiException(ErrorCode.MEMBER_NOT_FOUND);
        }
        if (dto == null || dto.getCurrentPassword() == null || dto.getCurrentPassword().isBlank()) {
            throw new ApiException(ErrorCode.MISSING_REQUIRED_FIELD, "현재 비밀번호를 입력해 주세요.");
        }
        if (!memberService.isMember(id, dto.getCurrentPassword())) {
            throw new ApiException(ErrorCode.LOGIN_FAILED, "현재 비밀번호가 일치하지 않습니다.");
        }
        if (dto.getNewPassword() == null || dto.getNewPassword().length() < 8) {
            throw new ApiException(ErrorCode.PASSWORD_TOO_SHORT);
        }

        boolean hasPermission = mailService.consumeMypageVerification(id);
        if (!hasPermission) {
            throw new ApiException(ErrorCode.EMAIL_VERIFICATION_REQUIRED);
        }

        if (!memberService.updatePassword(id, dto.getNewPassword())) {
            throw new ApiException(ErrorCode.PASSWORD_CHANGE_FAILED);
        }

        // 비밀번호 변경 시 이전 발급된 모든 세션의 토큰 일괄 무효화
        tokenManagementService.invalidateMemberTokens(id);

        return ResWrapper.done("비밀번호가 성공적으로 변경되었습니다.");
    }
}
