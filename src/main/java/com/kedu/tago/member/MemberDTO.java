package com.kedu.tago.member;

import lombok.*;

import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberDTO {

    private String id;
    private String pw;
    private String name;
    private String phone;
    private String email;
    private String zipcode;
    private String address1;
    private String address2;
    private Timestamp created_at;

}
