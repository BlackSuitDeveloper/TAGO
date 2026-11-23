package com.kedu.tago.auth;

import com.kedu.tago.auth.dto.FindIdRequestDTO;
import com.kedu.tago.auth.dto.ResetPasswordRequestDTO;
import com.kedu.tago.member.MemberService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final MailService mailService;
    private final MemberService memberService;

    public AuthController(MailService mailService, MemberService memberService) {
        this.mailService = mailService;
        this.memberService = memberService;
    }

    // ==========================================
    // 1. 아이디 찾기 (이메일 인증)
    // ==========================================

    // 1-1. 아이디 찾기 인증번호 발송 (가입 시 등록된 이름 + 이메일 검증)
    @PostMapping("/find-id/send-code")
    public ResponseEntity<?> sendFindIdCode(@RequestBody FindIdRequestDTO dto) {
        if (dto.getName() == null || dto.getName().trim().isEmpty() ||
            dto.getEmail() == null || dto.getEmail().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("이름과 이메일을 모두 입력해 주세요.");
        }

        String memberId = memberService.findIdByNameAndEmail(dto.getName().trim(), dto.getEmail().trim());
        if (memberId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("입력하신 정보와 일치하는 회원 정보를 찾을 수 없습니다.");
        }

        mailService.sendFindIdCode(dto.getEmail().trim());
        return ResponseEntity.ok(Map.of("message", "인증번호가 가입된 이메일로 발송되었습니다. (5분 유효)"));
    }

    // 1-2. 아이디 찾기 인증번호 확인 및 아이디 반환
    @PostMapping("/find-id/verify")
    public ResponseEntity<?> verifyFindIdCode(@RequestBody FindIdRequestDTO dto) {
        if (dto.getEmail() == null || dto.getCode() == null) {
            return ResponseEntity.badRequest().body("이메일과 인증번호를 입력해 주세요.");
        }

        boolean isValid = mailService.verifyFindIdCode(dto.getEmail().trim(), dto.getCode().trim());
        if (!isValid) {
            return ResponseEntity.badRequest().body("인증번호가 일치하지 않거나 만료되었습니다.");
        }

        String memberId = memberService.findIdByNameAndEmail(dto.getName().trim(), dto.getEmail().trim());
        if (memberId == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("회원 정보를 찾을 수 없습니다.");
        }

        // 아이디 마스킹 (예: 'admin123' -> 'adm*****')
        String maskedId;
        if (memberId.length() <= 3) {
            maskedId = memberId.charAt(0) + "*".repeat(memberId.length() - 1);
        } else {
            maskedId = memberId.substring(0, 3) + "*".repeat(memberId.length() - 3);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("message", "인증이 완료되었습니다.");
        result.put("id", memberId);
        result.put("maskedId", maskedId);

        return ResponseEntity.ok(result);
    }

    // ==========================================
    // 2. 비밀번호 재설정 (로그인 화면용 이메일 인증)
    // ==========================================

    // 2-1. 비밀번호 재설정 인증번호 발송 (아이디 + 가입 이메일 검증)
    @PostMapping("/reset-password/send-code")
    public ResponseEntity<?> sendResetPwCode(@RequestBody ResetPasswordRequestDTO dto) {
        if (dto.getId() == null || dto.getId().trim().isEmpty() ||
            dto.getEmail() == null || dto.getEmail().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("아이디와 이메일을 모두 입력해 주세요.");
        }

        boolean exists = memberService.validateMemberByIdAndEmail(dto.getId().trim(), dto.getEmail().trim());
        if (!exists) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("입력하신 아이디와 이메일이 일치하는 회원을 찾을 수 없습니다.");
        }

        mailService.sendResetPasswordCode(dto.getEmail().trim());
        return ResponseEntity.ok(Map.of("message", "인증번호가 가입된 이메일로 발송되었습니다. (5분 유효)"));
    }

    // 2-2. 비밀번호 재설정 인증번호 확인 (통과 시 재설정 토큰 발급)
    @PostMapping("/reset-password/verify")
    public ResponseEntity<?> verifyResetPwCode(@RequestBody ResetPasswordRequestDTO dto) {
        if (dto.getEmail() == null || dto.getCode() == null) {
            return ResponseEntity.badRequest().body("이메일과 인증번호를 입력해 주세요.");
        }

        String resetToken = mailService.verifyResetPasswordCode(dto.getEmail().trim(), dto.getCode().trim());
        if (resetToken == null) {
            return ResponseEntity.badRequest().body("인증번호가 일치하지 않거나 만료되었습니다.");
        }

        return ResponseEntity.ok(Map.of(
                "message", "이메일 인증이 완료되었습니다.",
                "resetToken", resetToken
        ));
    }

    // 2-3. 새 비밀번호 재설정 (Bcrypt 단방향 암호화 적용)
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequestDTO dto) {
        if (dto.getId() == null || dto.getEmail() == null ||
            dto.getResetToken() == null || dto.getNewPassword() == null) {
            return ResponseEntity.badRequest().body("필수 정보가 누락되었습니다.");
        }

        if (dto.getNewPassword().length() < 8) {
            return ResponseEntity.badRequest().body("비밀번호는 최소 8자리 이상이어야 합니다.");
        }

        // 재설정 토큰 유효성 검증
        boolean isTokenValid = mailService.validateAndConsumeResetToken(dto.getResetToken().trim(), dto.getEmail().trim());
        if (!isTokenValid) {
            return ResponseEntity.badRequest().body("인증 세션이 만료되었습니다. 처음부터 다시 시도해 주세요.");
        }

        // DB에 BCrypt 암호화된 비밀번호 업데이트
        boolean updated = memberService.updatePassword(dto.getId().trim(), dto.getNewPassword());
        if (!updated) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("비밀번호 변경에 실패했습니다.");
        }

        return ResponseEntity.ok(Map.of("message", "비밀번호가 성공적으로 변경되었습니다. 새로운 비밀번호로 로그인해 주세요."));
    }
}
