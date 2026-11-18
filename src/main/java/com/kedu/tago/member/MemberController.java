package com.kedu.tago.member;

import com.kedu.tago.auth.dto.AuthLoginRequestDTO;
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

    private MemberService mServ;
    private JwtUtil jwt;

    MemberController(MemberService mServ, JwtUtil jwt) {
        this.mServ = mServ;
        this.jwt = jwt;
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
}

