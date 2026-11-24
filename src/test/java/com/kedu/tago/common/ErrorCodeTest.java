package com.kedu.tago.common;

import com.kedu.tago.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorCodeTest {

    @Test
    @DisplayName("에러 코드 번호는 중복되지 않는다")
    void codesAreUnique() {
        Set<Integer> codes = Arrays.stream(ErrorCode.values()).map(ErrorCode::getCode).collect(Collectors.toSet());
        assertEquals(ErrorCode.values().length, codes.size());
    }

    @Test
    @DisplayName("번호 대역이 종류와 맞다: 1xxx 공통, 2xxx 인증, 3xxx 회원, 4xxx 운행/좌석, 5xxx 예매/결제, 9xxx 서버")
    void codeRangesMatchCategory() {
        for (ErrorCode c : ErrorCode.values()) {
            int band = c.getCode() / 1000;
            assertTrue(Set.of(1, 2, 3, 4, 5, 9).contains(band), c + " 은(는) 정의되지 않은 대역: " + c.getCode());
            String name = c.name();
            if (band == 2) {
                assertTrue(c.getStatus().value() == 401 || c.getStatus().value() == 403, name + " 인증/인가 코드는 401 또는 403");
            }
            if (band == 9) {
                assertEquals(500, c.getStatus().value(), name);
            }
        }
    }

    @Test
    @DisplayName("모든 코드는 비어 있지 않은 기본 문구를 가진다")
    void everyCodeHasMessage() {
        for (ErrorCode c : ErrorCode.values()) {
            assertTrue(c.getMessage() != null && !c.getMessage().isBlank(), c.name());
        }
    }
}
