package com.kedu.tago.member;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.kedu.tago.commons.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/member")
public class MemberController {

    private MemberService mServ;
    private JwtUtil jwt;

    MemberController(MemberService mServ, JwtUtil jwt) {
        this.mServ = mServ;
        this.jwt = jwt;
    }

    // 회원가입
    @PostMapping
    public int signup(@RequestBody MemberDTO dto) {
        return mServ.insertMember(dto);
    }

    // 아이디 중복 체크
    @GetMapping("/checkId")
    public int checkId(@RequestParam String id) {
        return mServ.checkId(id);
    }

    // 회원 정보 조회
    @GetMapping("/mypage")
    public ResponseEntity<MemberDTO> mypage(HttpServletRequest request) {
        String id = (String) request.getAttribute("id");
        MemberDTO dto = mServ.selectMember(id);
        dto.setPw(null);
        return ResponseEntity.ok(dto);
    }

    // 회원 정보 수정
    @PutMapping
    public ResponseEntity<String> updateMember(@RequestBody MemberDTO dto, HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if(authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            DecodedJWT dToken = jwt.verifyToken(token);
            String id = dToken.getSubject();

            dto.setId(id);
            mServ.updateMember(dto);
            return ResponseEntity.ok("수정 완료");
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("인증 실패");
    }

    // 회원 탈퇴
    @DeleteMapping
    public ResponseEntity<String> deleteId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if(authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            DecodedJWT dToken = jwt.verifyToken(token);
            String id = dToken.getSubject();

            mServ.deleteId(id);
            return ResponseEntity.ok("탈퇴 완료");
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("잘못된 접근입니다.");
    }
}
