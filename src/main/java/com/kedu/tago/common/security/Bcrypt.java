package com.kedu.tago.common.security;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Component;

/** BCrypt 비밀번호 해시·검증 유틸 */
@Component
public class Bcrypt {

    /** 비밀번호 해시 생성 */
    public String hashPassword(String plainPassword) {
        if(plainPassword == null) return null;
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(10));
    }

    /** 비밀번호와 해시 일치 여부 */
    public boolean checkPassword(String plainPassword, String hashedPassword) {
        if(plainPassword == null || hashedPassword == null) return false;
        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
