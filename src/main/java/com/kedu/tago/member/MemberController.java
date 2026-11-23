package com.kedu.tago.member;

import com.kedu.tago.auth.MailService;
import com.kedu.tago.auth.dto.AuthLoginRequestDTO;
import com.kedu.tago.auth.dto.MypagePasswordRequestDTO;
import com.kedu.tago.common.utils.JwtUtil;
import com.kedu.tago.member.dto.MemberProfileResponseDTO;
import com.kedu.tago.member.dto.MemberSignupRequestDTO;
import com.kedu.tago.member.dto.MemberUpdateRequestDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<?> login(@RequestBody AuthLoginRequestDTO dto) {
        System.out.println(dto.getId() + " : " + dto.getPw());
        if (mServ.isMember(dto.getId(), dto.getPw())) {
            String token = jwt.createToken(dto.getId());
            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "id", dto.getId()
            ));
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("아이디 또는 비밀번호가 올바르지 않습니다.");
    }

    // 회원가입
    @PostMapping
    public int signup(@RequestBody MemberSignupRequestDTO dto) {
        return mServ.insertMember(dto);
    }

    // 아이디 중복 체크
    @GetMapping("/checkId")
    public int checkId(@RequestParam String id) {
        return mServ.checkId(id);
    }

    // 회원 정보 조회
    @GetMapping("/mypage")
    public ResponseEntity<MemberProfileResponseDTO> mypage(HttpServletRequest request) {
        String id = (String) request.getAttribute("id");
        MemberProfileResponseDTO dto = mServ.selectMember(id);
        return ResponseEntity.ok(dto);
    }

    // 회원 정보 수정
    @PutMapping
    public ResponseEntity<String> updateMember(@RequestBody MemberUpdateRequestDTO dto, HttpServletRequest request) {
        String id = (String) request.getAttribute("id");
        dto.setId(id);
        mServ.updateMember(dto);
        return ResponseEntity.ok("수정 완료");
    }

    // 회원 탈퇴
    @DeleteMapping
    public ResponseEntity<String> deleteId(HttpServletRequest request) {
        String id = (String) request.getAttribute("id");
        mServ.deleteId(id);
        return ResponseEntity.ok("탈퇴 완료");
    }

    // 마이페이지 - 이메일 인증번호 발송
    @PostMapping("/password/send-code")
    public ResponseEntity<?> sendMypagePasswordCode(HttpServletRequest request) {
        String id = (String) request.getAttribute("id");
        MemberProfileResponseDTO profile = mServ.selectMember(id);
        if (profile == null || profile.getEmail() == null || profile.getEmail().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("회원 정보 또는 등록된 이메일을 찾을 수 없습니다.");
        }

        mailService.sendMypagePasswordCode(profile.getEmail().trim());
        return ResponseEntity.ok(Map.of(
                "message", "회원님의 가입 이메일(" + profile.getEmail() + ")로 인증번호가 발송되었습니다.",
                "email", profile.getEmail()
        ));
    }

    // 마이페이지 - 이메일 인증번호 확인
    @PostMapping("/password/verify")
    public ResponseEntity<?> verifyMypagePasswordCode(@RequestBody MypagePasswordRequestDTO dto, HttpServletRequest request) {
        String id = (String) request.getAttribute("id");
        MemberProfileResponseDTO profile = mServ.selectMember(id);
        if (profile == null || profile.getEmail() == null) {
            return ResponseEntity.badRequest().body("회원 정보를 찾을 수 없습니다.");
        }

        if (dto.getCode() == null || dto.getCode().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("인증번호를 입력해 주세요.");
        }

        boolean isValid = mailService.verifyMypagePasswordCode(profile.getEmail().trim(), dto.getCode().trim());
        if (!isValid) {
            return ResponseEntity.badRequest().body("인증번호가 일치하지 않거나 만료되었습니다.");
        }

        return ResponseEntity.ok(Map.of("message", "이메일 본인인증이 완료되었습니다."));
    }

    // 마이페이지 - 새 비밀번호 변경 (BCrypt 암호화)
    @PutMapping("/password")
    public ResponseEntity<?> updateMypagePassword(@RequestBody MypagePasswordRequestDTO dto, HttpServletRequest request) {
        String id = (String) request.getAttribute("id");
        MemberProfileResponseDTO profile = mServ.selectMember(id);
        if (profile == null || profile.getEmail() == null) {
            return ResponseEntity.badRequest().body("회원 정보를 찾을 수 없습니다.");
        }

        if (dto.getNewPassword() == null || dto.getNewPassword().length() < 8) {
            return ResponseEntity.badRequest().body("새 비밀번호는 최소 8자리 이상이어야 합니다.");
        }

        boolean hasPermission = mailService.consumeMypageVerification(profile.getEmail().trim());
        if (!hasPermission) {
            return ResponseEntity.badRequest().body("이메일 인증이 완료되지 않았거나 인증 세션이 만료되었습니다. 다시 인증해 주세요.");
        }

        boolean success = mServ.updatePassword(id, dto.getNewPassword());
        if (!success) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("비밀번호 변경에 실패했습니다.");
        }

        return ResponseEntity.ok(Map.of("message", "비밀번호가 성공적으로 변경되었습니다."));
    }
}

