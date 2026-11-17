package com.kedu.tago.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberSignupRequestDTO {

    private String id;
    private String pw;
    private String name;
    private String phone;
    private String email;
    private String zipcode;
    private String address1;
    private String address2;
}
