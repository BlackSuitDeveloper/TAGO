package com.kedu.tago.member;

/** 회원 권한 (member.role 컬럼과 JWT role 클레임에 이름 그대로 저장, ADMIN 은 USER 권한 포함) */
public enum Role {
    ADMIN,
    USER;

    /** 저장된 값을 Role 로 변환 (없거나 모르는 값이면 USER) */
    public static Role from(String value) {
        if (value == null) {
            return USER;
        }
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return USER;
        }
    }

    /** required 권한을 충족하는지 여부 (ADMIN 은 모두 충족) */
    public boolean satisfies(Role required) {
        return this == ADMIN || this == required;
    }
}
