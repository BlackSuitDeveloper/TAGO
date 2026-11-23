package com.kedu.tago.member;

import com.kedu.tago.auth.dto.AuthLoginRequestDTO;
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
@RequestMapping("/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final MailService mailService;

    /** 회원가입 (POST /api/member) */
    @PostMapping
    public ResWrapper<Void> signup(@RequestBody MemberSignupRequestDTO dto) {
        if (memberService.insertMember(dto) != 1) {
            throw new ApiException(ErrorCode.SIGNUP_FAILED);
        }
        return ResWrapper.done("회원가입이 완료되었습니다.");
    }

    /** 아이디 사용 가능 여부 (GET /api/member/checkId) */
    @GetMapping("/checkId")
    public ResWrapper<Boolean> checkId(@RequestParam String id) {
        return ResWrapper.ok(memberService.checkId(id) == 0);
    }

    /** 내 정보 조회 (GET /api/member/mypage) */
    @GetMapping("/mypage")
    public ResWrapper<MemberProfileResponseDTO> mypage(HttpServletRequest request) {
        String id = (String) request.getAttribute("id");
        return ResWrapper.ok(memberService.selectMember(id));
    }

    /** 내 정보 수정 (PUT /api/member) */
    @PutMapping
    public ResWrapper<Void> updateMember(@RequestBody MemberUpdateRequestDTO dto, HttpServletRequest request) {
        String id = (String) request.getAttribute("id");
        dto.setId(id);
        memberService.updateMember(dto);
        return ResWrapper.done("수정 완료");
    }

    /** 회원 탈퇴 (DELETE /api/member) */
    @DeleteMapping
    public ResWrapper<Void> deleteId(HttpServletRequest request) {
        String id = (String) request.getAttribute("id");
        memberService.deleteId(id);
        return ResWrapper.done("탈퇴 완료");
    }

    /** 비밀번호 변경용 이메일 인증번호 발송 (POST /api/member/password/send-code) */
    @PostMapping("/password/send-code")
    public ResWrapper<Map<String, String>> sendMypagePasswordCode(HttpServletRequest request) {
        String id = (String) request.getAttribute("id");
        MemberProfileResponseDTO profile = memberService.selectMember(id);

        if (profile == null || profile.getEmail() == null || profile.getEmail().trim().isEmpty()) {
            throw new ApiException(ErrorCode.EMAIL_NOT_REGISTERED);
        }

        mailService.sendMypagePasswordCode(profile.getEmail().trim());
        return ResWrapper.ok(
                Map.of("email", profile.getEmail()),
                "회원님의 가입 이메일(" + profile.getEmail() + ")로 인증번호가 발송되었습니다.");
    }

    /** 비밀번호 변경용 이메일 인증번호 확인 (POST /api/member/password/verify) */
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

        boolean isValid = mailService.verifyMypagePasswordCode(profile.getEmail().trim(), dto.getCode().trim());
        if (!isValid) {
            throw new ApiException(ErrorCode.CODE_MISMATCH);
        }
        return ResWrapper.done("이메일 본인인증이 완료되었습니다.");
    }

    /** 비밀번호 변경 (PUT /api/member/password) */
    @PutMapping("/password")
    public ResWrapper<Void> updateMypagePassword(@RequestBody MypagePasswordRequestDTO dto, HttpServletRequest request) {
        String id = (String) request.getAttribute("id");
        MemberProfileResponseDTO profile = memberService.selectMember(id);

        if (profile == null || profile.getEmail() == null) {
            throw new ApiException(ErrorCode.MEMBER_NOT_FOUND);
        }
        if (dto.getNewPassword() == null || dto.getNewPassword().length() < 8) {
            throw new ApiException(ErrorCode.PASSWORD_TOO_SHORT);
        }

        boolean hasPermission = mailService.consumeMypageVerification(profile.getEmail().trim());
        if (!hasPermission) {
            throw new ApiException(ErrorCode.EMAIL_VERIFICATION_REQUIRED);
        }

        if (!memberService.updatePassword(id, dto.getNewPassword())) {
            throw new ApiException(ErrorCode.PASSWORD_CHANGE_FAILED);
        }
        return ResWrapper.done("비밀번호가 성공적으로 변경되었습니다.");
    }
}
