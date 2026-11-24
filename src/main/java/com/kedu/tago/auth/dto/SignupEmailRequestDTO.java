package com.kedu.tago.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 회원가입 이메일 인증 발송 및 확인 DTO */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignupEmailRequestDTO {

    private String email;
    private String code;
}
