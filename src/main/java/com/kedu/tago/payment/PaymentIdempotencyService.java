package com.kedu.tago.payment;

import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.payment.dto.PaymentResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 결제 멱등성: 같은 Idempotency-Key 로 온 요청은 한 번만 처리한다.
 *
 * idem:payment:{회원}:{키}:processing  = "1" (TTL)  처리 중 표시. SET NX EX 한 번으로 만들어 TTL 이 빠질 일이 없다
 * idem:payment:{회원}:{키}             Hash         처리 완료 응답 (같은 키로 또 오면 그대로 돌려줌)
 *
 * 처리 중에 같은 키로 오면 409, 처리 중 실패하면 표시를 지워 같은 키로 다시 시도할 수 있게 한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentIdempotencyService {

    private static final String KEY_PREFIX = "idem:payment:";
    private static final String PROCESSING_SUFFIX = ":processing";
    private static final Duration PROCESSING_TTL = Duration.ofMinutes(5); // 서버가 처리 중 죽어도 5분 뒤 풀림
    private static final Duration DONE_TTL = Duration.ofHours(24);
    private static final int MAX_KEY_LENGTH = 100;

    private final StringRedisTemplate redis;

    /** 처리 시작. 처음 온 요청이면 null, 이미 끝난 요청이면 저장된 응답을 돌려준다 */
    public PaymentResponseDTO begin(String memberId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        String key = key(memberId, idempotencyKey);
        PaymentResponseDTO done = savedResponse(key);
        if (done != null) {
            return done;
        }
        Boolean first = redis.opsForValue().setIfAbsent(key + PROCESSING_SUFFIX, "1", PROCESSING_TTL);
        if (Boolean.TRUE.equals(first)) {
            return null;
        }
        // 다른 요청이 처리 중이거나, 확인 직후 막 끝났다
        done = savedResponse(key);
        if (done != null) {
            return done;
        }
        throw new ApiException(ErrorCode.PAYMENT_IN_PROGRESS);
    }

    /** 처리 성공: 응답을 저장해 두고 처리 중 표시를 지운다 */
    public void complete(String memberId, String idempotencyKey, PaymentResponseDTO response) {
        String key = key(memberId, idempotencyKey);
        Map<String, String> fields = new HashMap<>();
        put(fields, "paymentSeq", response.getPaymentSeq());
        put(fields, "reservationId", response.getReservationId());
        put(fields, "ticketNumber", response.getTicketNumber());
        put(fields, "status", response.getStatus());
        put(fields, "amount", response.getAmount());
        put(fields, "approvedAt", response.getApprovedAt());
        put(fields, "message", response.getMessage());
        redis.opsForHash().putAll(key, fields);
        redis.expire(key, DONE_TTL);
        redis.delete(key + PROCESSING_SUFFIX);
    }

    /** 처리 실패: 같은 키로 다시 시도할 수 있게 지운다 */
    public void abort(String memberId, String idempotencyKey) {
        String key = key(memberId, idempotencyKey);
        redis.delete(List.of(key, key + PROCESSING_SUFFIX));
    }

    private PaymentResponseDTO savedResponse(String key) {
        Map<Object, Object> saved = redis.opsForHash().entries(key);
        return (saved == null || saved.isEmpty()) ? null : fromHash(saved);
    }

    private String key(String memberId, String idempotencyKey) {
        return KEY_PREFIX + memberId + ":" + idempotencyKey;
    }

    private static void put(Map<String, String> fields, String name, Object value) {
        if (value != null) {
            fields.put(name, String.valueOf(value));
        }
    }

    private static PaymentResponseDTO fromHash(Map<Object, Object> h) {
        return PaymentResponseDTO.builder()
                .paymentSeq(h.get("paymentSeq") != null ? Long.valueOf((String) h.get("paymentSeq")) : null)
                .reservationId(h.get("reservationId") != null ? Long.valueOf((String) h.get("reservationId")) : null)
                .ticketNumber((String) h.get("ticketNumber"))
                .status((String) h.get("status"))
                .amount(h.get("amount") != null ? Integer.valueOf((String) h.get("amount")) : null)
                .approvedAt(h.get("approvedAt") != null ? LocalDateTime.parse((String) h.get("approvedAt")) : null)
                .message((String) h.get("message"))
                .build();
    }
}
