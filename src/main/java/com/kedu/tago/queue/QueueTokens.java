package com.kedu.tago.queue;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

/** 대기열 토큰 메타 저장·생존 신호 갱신 (대기열 패키지 내부용) */
final class QueueTokens {

    private QueueTokens() {
    }

    /** 새 토큰 메타 저장 (스케줄러가 유령으로 오판하지 않도록 진입 직전에 저장) */
    static void saveMeta(StringRedisTemplate redis, String token, String memberId, String scheduleSeq, long now) {
        String metaKey = QueueKeys.meta(token);
        redis.opsForHash().putAll(metaKey, Map.of(
                "memberId", memberId,
                "scheduleSeq", scheduleSeq,
                "lastSeen", String.valueOf(now)
        ));
        redis.expire(metaKey, QueueKeys.META_TTL);
    }

    /** 생존 신호(lastSeen) 갱신 + 메타·회원 매핑 TTL 연장 (오래 기다려도 매핑이 먼저 사라지지 않도록) */
    static void touch(StringRedisTemplate redis, String scheduleSeq, String memberId, String token, long now) {
        String metaKey = QueueKeys.meta(token);
        redis.opsForHash().put(metaKey, "lastSeen", String.valueOf(now));
        redis.expire(metaKey, QueueKeys.META_TTL);
        if (memberId != null) {
            redis.expire(QueueKeys.user(scheduleSeq, memberId), QueueKeys.META_TTL);
        }
    }

    /** 메타의 lastSeen (깨진 값은 0 = 유령으로 본다) */
    static long lastSeen(Map<Object, Object> meta) {
        Object value = meta.get("lastSeen");
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
