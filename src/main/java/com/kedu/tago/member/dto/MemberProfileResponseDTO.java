package com.kedu.tago.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import com.kedu.tago.member.Role;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberProfileResponseDTO {

    private String id;
    private String name;
    private String phone;
    private String email;
    private String zipcode;
    private String address1;
    private String address2;
    private Role role;
    private Timestamp created_at;
}
