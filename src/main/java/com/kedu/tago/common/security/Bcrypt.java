package com.kedu.tago.common.security;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Component;

@Component
public class Bcrypt {

    public String hashPassword(String plainPassword) {
        if(plainPassword == null) return null;
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(10));
    }

    public boolean checkPassword(String plainPassword, String hashedPassword) {
        if(plainPassword == null || hashedPassword == null) return false;
        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
