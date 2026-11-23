package com.kedu.tago.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequestDTO {
    private String id;
    private String email;
    private String code;
    private String resetToken;
    private String newPassword;
}
