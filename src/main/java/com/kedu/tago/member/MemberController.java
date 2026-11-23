package com.kedu.tago.member;

import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.auth.MailService;
import com.kedu.tago.member.dto.AuthLoginRequestDTO;
import com.kedu.tago.member.dto.MypagePasswordRequestDTO;
import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.response.ResWrapper;
import com.kedu.tago.common.security.JwtUtil;
import com.kedu.tago.member.dto.MemberProfileResponseDTO;
import com.kedu.tago.member.dto.MemberSignupRequestDTO;
import com.kedu.tago.member.dto.MemberUpdateRequestDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/member")
public class MemberController {

    private final MemberService mServ;
    private final JwtUtil jwt;
    private final MailService mailService;

    MemberController(MemberService mServ, JwtUtil jwt, MailService mailService) {
        this.mServ = mServ;
        this.jwt = jwt;
        this.mailService = mailService;
    }

    @PostMapping("/login")
    public ResWrapper<Map<String, String>> login(@RequestBody AuthLoginRequestDTO dto) {
        if (!mServ.isMember(dto.getId(), dto.getPw())) {
            throw new ApiException(ErrorCode.LOGIN_FAILED);
        }
        Role role = mServ.getRole(dto.getId());
        String token = jwt.createToken(dto.getId(), role);
        return ResWrapper.ok(Map.of("token", token, "id", dto.getId(), "role", role.name()));
    }

    // 회원가입
    @PostMapping
    public ResWrapper<Void> signup(@RequestBody MemberSignupRequestDTO dto) {
        if (mServ.insertMember(dto) != 1) {
            throw new ApiException(ErrorCode.SIGNUP_FAILED);
        }
        return ResWrapper.done("회원가입이 완료되었습니다.");
    }

    // 아이디 중복 체크 (data: 사용 가능하면 true)
    @GetMapping("/checkId")
    public ResWrapper<Boolean> checkId(@RequestParam String id) {
        return ResWrapper.ok(mServ.checkId(id) == 0);
    }

    // 회원 정보 조회
    @GetMapping("/mypage")
    public ResWrapper<MemberProfileResponseDTO> mypage(HttpServletRequest request) {
        String id = (String) request.getAttribute("id");
        return ResWrapper.ok(mServ.selectMember(id));
    }

    // 회원 정보 수정
    @PutMapping
    public ResWrapper<Void> updateMember(@RequestBody MemberUpdateRequestDTO dto, HttpServletRequest request) {
        String id = (String) request.getAttribute("id");
        dto.setId(id);
        mServ.updateMember(dto);
        return ResWrapper.done("수정 완료");
    }

    // 회원 탈퇴
    @DeleteMapping
    public ResWrapper<Void> deleteId(HttpServletRequest request) {
        String id = (String) request.getAttribute("id");
        mServ.deleteId(id);
        return ResWrapper.done("탈퇴 완료");
    }

    // 마이페이지 - 이메일 인증번호 발송
    @PostMapping("/password/send-code")
    public ResWrapper<Map<String, String>> sendMypagePasswordCode(HttpServletRequest request) {
        String id = (String) request.getAttribute("id");
        MemberProfileResponseDTO profile = mServ.selectMember(id);

        if (profile == null || profile.getEmail() == null || profile.getEmail().trim().isEmpty()) {
            throw new ApiException(ErrorCode.EMAIL_NOT_REGISTERED);
        }

        mailService.sendMypagePasswordCode(profile.getEmail().trim());
        return ResWrapper.ok(
                Map.of("email", profile.getEmail()),
                "회원님의 가입 이메일(" + profile.getEmail() + ")로 인증번호가 발송되었습니다.");
    }

    // 마이페이지 - 이메일 인증번호 확인
    @PostMapping("/password/verify")
    public ResWrapper<Void> verifyMypagePasswordCode(@RequestBody MypagePasswordRequestDTO dto, HttpServletRequest request) {
        String id = (String) request.getAttribute("id");
        MemberProfileResponseDTO profile = mServ.selectMember(id);

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

    // 마이페이지 - 새 비밀번호 변경 (BCrypt 암호화)
    @PutMapping("/password")
    public ResWrapper<Void> updateMypagePassword(@RequestBody MypagePasswordRequestDTO dto, HttpServletRequest request) {
        String id = (String) request.getAttribute("id");
        MemberProfileResponseDTO profile = mServ.selectMember(id);

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

        if (!mServ.updatePassword(id, dto.getNewPassword())) {
            throw new ApiException(ErrorCode.PASSWORD_CHANGE_FAILED);
        }
        return ResWrapper.done("비밀번호가 성공적으로 변경되었습니다.");
    }
}
