package com.kedu.tago.auth;

import com.kedu.tago.common.utils.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
//
//    private JwtUtil jwt;
//    private MemberService mServ;
//    public AuthController(JwtUtil jwt, MemberService mServ) {
//        this.jwt = jwt;
//        this.mServ = mServ;
//    }
//
//    @PostMapping("/login")
//    public ResponseEntity<Map<String, String>> login(@RequestBody AuthDTO dto) {
//        // Session -> JWT (Json Web Token)
//        // Cookie -> Session -> Token 방식으로 발전함
//        // 웹서버는 Http -> Stateless(상태정보를 기억하지 않음)
//        String id = dto.getId();
//        String pw = dto.getPw();
//        if(mServ.isLogin(dto.getId(), dto.getPw())) {
//            Map<String, String> result = new HashMap<>();
//            String token = this.jwt.createToken(id);
//            result.put("token", token);
//            result.put("id", id);
//            return ResponseEntity.ok(result);
//        } else {
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
//        }
//    }
}