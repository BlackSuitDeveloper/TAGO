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
import java.util.Map;

/**
 * 결제 멱등성: 같은 Idempotency-Key 로 온 요청은 한 번만 처리한다.
 *
 * idem:payment:{회원}:{키} (Hash)
 *   state = PROCESSING  처리 중 (같은 키로 또 오면 409)
 *   state = DONE        처리 완료 (같은 키로 또 오면 저장해 둔 응답을 그대로 돌려줌)
 * 처리 중 실패하면 키를 지워서, 같은 키로 다시 시도할 수 있게 한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentIdempotencyService {

    private static final String KEY_PREFIX = "idem:payment:";
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
        Boolean first = redis.opsForHash().putIfAbsent(key, "state", "PROCESSING");
        if (Boolean.TRUE.equals(first)) {
            redis.expire(key, PROCESSING_TTL);
            return null;
        }
        Map<Object, Object> saved = redis.opsForHash().entries(key);
        if ("DONE".equals(saved.get("state"))) {
            return fromHash(saved);
        }
        throw new ApiException(ErrorCode.PAYMENT_IN_PROGRESS);
    }

    /** 처리 성공: 응답을 저장해 둔다 */
    public void complete(String memberId, String idempotencyKey, PaymentResponseDTO response) {
        String key = key(memberId, idempotencyKey);
        Map<String, String> fields = new HashMap<>();
        fields.put("state", "DONE");
        put(fields, "reservationId", response.getReservationId());
        put(fields, "ticketNumber", response.getTicketNumber());
        put(fields, "status", response.getStatus());
        put(fields, "amount", response.getAmount());
        put(fields, "approvedAt", response.getApprovedAt());
        put(fields, "message", response.getMessage());
        redis.opsForHash().putAll(key, fields);
        redis.expire(key, DONE_TTL);
    }

    /** 처리 실패: 같은 키로 다시 시도할 수 있게 지운다 */
    public void abort(String memberId, String idempotencyKey) {
        redis.delete(key(memberId, idempotencyKey));
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
                .reservationId(h.get("reservationId") != null ? Long.valueOf((String) h.get("reservationId")) : null)
                .ticketNumber((String) h.get("ticketNumber"))
                .status((String) h.get("status"))
                .amount(h.get("amount") != null ? Integer.valueOf((String) h.get("amount")) : null)
                .approvedAt(h.get("approvedAt") != null ? LocalDateTime.parse((String) h.get("approvedAt")) : null)
                .message((String) h.get("message"))
                .build();
    }
}
