package com.kedu.tago.member;

/**
 * 회원 권한. member.role 컬럼과 JWT 의 role 클레임에 이름(ADMIN, USER) 그대로 저장된다.
 *
 * <ul>
 *   <li>USER  : 일반 회원 (가입하면 기본값)</li>
 *   <li>ADMIN : 관리자. USER 가 할 수 있는 것을 모두 할 수 있고, 관리자 전용 API 도 쓸 수 있다.</li>
 * </ul>
 */
public enum Role {
    ADMIN,
    USER;

    /** 저장된 값 -> Role. 비어 있거나 모르는 값(예전 토큰의 guest 등)은 가장 낮은 권한인 USER 로 본다. */
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

    /** 이 권한이 required 를 충족하는가. ADMIN 은 모든 권한을 충족한다. */
    public boolean satisfies(Role required) {
        return this == ADMIN || this == required;
    }
}
