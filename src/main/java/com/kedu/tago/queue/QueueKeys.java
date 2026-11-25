package com.kedu.tago.queue;

import java.time.Duration;

/**
 * 대기열 Redis 키. 키 이름은 여기서만 만든다.
 *
 * tago:queue:wait:{s}           ZSet(token, 진입 시각)  대기열
 * tago:queue:active:{s}:{token} = memberId (TTL)        입장 허용 (TTL 이 지나면 자동 퇴장)
 * tago:queue:meta:{token}       Hash(memberId, scheduleSeq, lastSeen)
 * tago:queue:user:{s}:{member}  = token                 회원+열차별 토큰 (멱등 진입)
 * tago:queue:schedules          Set(scheduleSeq)        대기자가 있는 운행 목록 (스케줄러가 돈다)
 */
public final class QueueKeys {

    public static final String WAIT_PREFIX = "tago:queue:wait:";
    public static final String ACTIVE_PREFIX = "tago:queue:active:";
    public static final String META_PREFIX = "tago:queue:meta:";
    public static final String USER_PREFIX = "tago:queue:user:";
    public static final String SCHEDULES = "tago:queue:schedules";

    /** 메타·회원 매핑 TTL (폴링할 때마다 연장) */
    public static final Duration META_TTL = Duration.ofMinutes(30);

    private QueueKeys() {
    }

    public static String waiting(Object scheduleSeq) {
        return WAIT_PREFIX + scheduleSeq;
    }

    public static String active(Object scheduleSeq, String token) {
        return ACTIVE_PREFIX + scheduleSeq + ":" + token;
    }

    public static String activePattern(Object scheduleSeq) {
        return ACTIVE_PREFIX + scheduleSeq + ":*";
    }

    public static String meta(String token) {
        return META_PREFIX + token;
    }

    public static String user(Object scheduleSeq, String memberId) {
        return USER_PREFIX + scheduleSeq + ":" + memberId;
    }
}
