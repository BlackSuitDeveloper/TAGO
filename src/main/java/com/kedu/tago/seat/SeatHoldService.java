package com.kedu.tago.seat;

import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.reservation.dto.ReservationRequestDTO.SeatSelection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 좌석 선점 서비스.
 *
 * held:seat:{s}:{car}:{seat} = userId (TTL)  좌석 단위 선점
 * held:user:{s}:{userId}     = {"car:seat"}  사용자 단위 선점 목록
 * held:expiry                = ZSet("s:car:seat", 만료 시각)  만료 알림용
 * lock:seat:{s}:{car}:{seat}                 Redisson 락 (몇 ms만 유지)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatHoldService {

    public static final int MAX_SEATS_PER_USER = 6;

    private static final String HELD_SEAT = "held:seat:";
    private static final String HELD_USER = "held:user:";
    private static final String HELD_EXPIRY = "held:expiry";
    private static final String LOCK_SEAT = "lock:seat:";
    private static final long LOCK_LEASE_SECONDS = 3;

    private final StringRedisTemplate redis;
    private final RedissonClient redisson;
    private final SeatMapper seatMapper;

    @Value("${tago.hold.ttl-seconds:600}")
    private long holdTtlSeconds;

    public long getHoldTtlSeconds() {
        return holdTtlSeconds;
    }

    /** 좌석 선점. 하나라도 이미 선점·확정된 좌석이면 전체 실패 */
    public LocalDateTime hold(Long scheduleSeq, String userId, List<SeatSelection> seats) {
        String userKey = userKey(scheduleSeq, userId);
        Long alreadyHeld = redis.opsForSet().size(userKey);
        if ((alreadyHeld == null ? 0 : alreadyHeld) + seats.size() > MAX_SEATS_PER_USER) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "1인 최대 " + MAX_SEATS_PER_USER + "석까지 선점할 수 있습니다.");
        }

        RLock lock = multiLock(scheduleSeq, seats);
        if (!tryLock(lock)) {
            throw new ApiException(ErrorCode.SEAT_ALREADY_TAKEN, "다른 사용자가 같은 좌석을 선점하는 중입니다.");
        }
        try {
            // 1. 이미 선점된 좌석 확인 (Redis)
            List<String> holders = redis.opsForValue().multiGet(seatKeys(scheduleSeq, seats));
            for (int i = 0; i < seats.size(); i++) {
                if (holders != null && holders.get(i) != null) {
                    throw seatTaken(seats.get(i));
                }
            }
            // 2. 이미 확정된 좌석 확인 (DB: seat 행이 있으면 판매 완료)
            for (SeatSelection s : seats) {
                if (seatMapper.selectSeat(scheduleSeq, s.getCarNo(), s.getSeatNo()) != null) {
                    throw seatTaken(s);
                }
            }
            // 3. 선점 저장
            Duration ttl = Duration.ofSeconds(holdTtlSeconds);
            long expireAtMs = System.currentTimeMillis() + ttl.toMillis();
            for (SeatSelection s : seats) {
                redis.opsForValue().set(seatKey(scheduleSeq, s), userId, ttl);
                redis.opsForSet().add(userKey, member(s));
                redis.opsForZSet().add(HELD_EXPIRY, expiryMember(scheduleSeq, s), expireAtMs);
            }
            redis.expire(userKey, ttl);
            return toLocalDateTime(expireAtMs);
        } finally {
            unlock(lock);
        }
    }

    /** 내가 선점 중인 좌석 목록 (만료됐거나 남의 것이 된 좌석은 제외) */
    public List<SeatSelection> getMyHolds(Long scheduleSeq, String userId) {
        Set<String> members = redis.opsForSet().members(userKey(scheduleSeq, userId));
        List<SeatSelection> result = new ArrayList<>();
        if (members == null || members.isEmpty()) {
            return result;
        }
        List<SeatSelection> candidates = new ArrayList<>();
        for (String m : members) {
            String[] parts = m.split(":");
            candidates.add(new SeatSelection(Integer.valueOf(parts[0]), parts[1]));
        }
        List<String> holders = redis.opsForValue().multiGet(seatKeys(scheduleSeq, candidates));
        for (int i = 0; i < candidates.size(); i++) {
            if (holders != null && userId.equals(holders.get(i))) {
                result.add(candidates.get(i));
            }
        }
        return result;
    }

    /** 결제 직전: 좌석 주인이 나인지 확인하고, 결제 처리 중 만료되지 않게 TTL 연장 */
    public void verifyAndExtend(Long scheduleSeq, String userId, List<SeatSelection> seats, long extendSeconds) {
        RLock lock = multiLock(scheduleSeq, seats);
        if (!tryLock(lock)) {
            throw new ApiException(ErrorCode.HOLD_EXPIRED);
        }
        try {
            List<String> keys = seatKeys(scheduleSeq, seats);
            List<String> holders = redis.opsForValue().multiGet(keys);
            for (int i = 0; i < seats.size(); i++) {
                if (holders == null || !userId.equals(holders.get(i))) {
                    throw new ApiException(ErrorCode.HOLD_EXPIRED);
                }
            }
            long expireAtMs = System.currentTimeMillis() + extendSeconds * 1000L;
            for (int i = 0; i < seats.size(); i++) {
                Long remain = redis.getExpire(keys.get(i), TimeUnit.SECONDS);
                if (remain != null && remain >= 0 && remain < extendSeconds) {
                    redis.expire(keys.get(i), Duration.ofSeconds(extendSeconds));
                    redis.opsForZSet().add(HELD_EXPIRY, expiryMember(scheduleSeq, seats.get(i)), expireAtMs);
                }
            }
            redis.expire(userKey(scheduleSeq, userId), Duration.ofSeconds(Math.max(extendSeconds, holdTtlSeconds)));
        } finally {
            unlock(lock);
        }
    }

    /** 내 선점 해제 (내 키만 삭제). 실제로 해제된 좌석 목록 반환 */
    public List<SeatSelection> release(Long scheduleSeq, String userId, List<SeatSelection> seats) {
        List<SeatSelection> released = new ArrayList<>();
        if (seats == null || seats.isEmpty()) {
            return released;
        }
        RLock lock = multiLock(scheduleSeq, seats);
        boolean locked = tryLock(lock, 1);
        try {
            List<String> keys = seatKeys(scheduleSeq, seats);
            List<String> holders = redis.opsForValue().multiGet(keys);
            for (int i = 0; i < seats.size(); i++) {
                if (holders != null && userId.equals(holders.get(i))) {
                    redis.delete(keys.get(i));
                    redis.opsForZSet().remove(HELD_EXPIRY, expiryMember(scheduleSeq, seats.get(i)));
                    released.add(seats.get(i));
                }
                redis.opsForSet().remove(userKey(scheduleSeq, userId), member(seats.get(i)));
            }
        } finally {
            if (locked) {
                unlock(lock);
            }
        }
        return released;
    }

    /** 결제 확정 후 선점 키 정리 (이미 DB 에 확정되었으므로 무조건 삭제) */
    public void clearAfterConfirm(Long scheduleSeq, String userId, List<SeatSelection> seats) {
        for (SeatSelection s : seats) {
            redis.delete(seatKey(scheduleSeq, s));
            redis.opsForZSet().remove(HELD_EXPIRY, expiryMember(scheduleSeq, s));
        }
        redis.delete(userKey(scheduleSeq, userId));
    }

    /** 정리 실패 시 대안: 선점 키를 1초 뒤 만료되게 바꿔 즉시 반납 (DB 에는 이미 확정됨) */
    public void expireSoon(Long scheduleSeq, String userId, List<SeatSelection> seats) {
        for (SeatSelection s : seats) {
            redis.expire(seatKey(scheduleSeq, s), Duration.ofSeconds(1));
        }
        redis.expire(userKey(scheduleSeq, userId), Duration.ofSeconds(1));
    }

    /** 호차의 좌석별 선점자 조회 (seatNo -> userId), MGET 한 번 */
    public Map<String, String> getHolders(Long scheduleSeq, int carNo, List<String> seatNos) {
        Map<String, String> result = new HashMap<>();
        if (seatNos.isEmpty()) {
            return result;
        }
        List<String> keys = new ArrayList<>(seatNos.size());
        for (String seatNo : seatNos) {
            keys.add(HELD_SEAT + scheduleSeq + ":" + carNo + ":" + seatNo);
        }
        List<String> holders = redis.opsForValue().multiGet(keys);
        for (int i = 0; i < seatNos.size(); i++) {
            if (holders != null && holders.get(i) != null) {
                result.put(seatNos.get(i), holders.get(i));
            }
        }
        return result;
    }

    /** TTL 로 만료된 선점 목록을 꺼낸다 ([scheduleSeq, carNo, seatNo]). 다시 선점·연장된 좌석은 제외 */
    public List<String[]> pollExpiredHolds() {
        List<String[]> result = new ArrayList<>();
        Set<String> expired = redis.opsForZSet().rangeByScore(HELD_EXPIRY, 0, System.currentTimeMillis());
        if (expired == null || expired.isEmpty()) {
            return result;
        }
        for (String m : expired) {
            if (Boolean.TRUE.equals(redis.hasKey(HELD_SEAT + m))) {
                continue; // 그 사이 다시 선점되었거나 연장됨
            }
            Long removed = redis.opsForZSet().remove(HELD_EXPIRY, m);
            if (removed != null && removed > 0) {
                result.add(m.split(":"));
            }
        }
        return result;
    }

    private RLock multiLock(Long scheduleSeq, List<SeatSelection> seats) {
        RLock[] locks = new RLock[seats.size()];
        for (int i = 0; i < seats.size(); i++) {
            locks[i] = redisson.getLock(LOCK_SEAT + scheduleSeq + ":" + member(seats.get(i)));
        }
        return locks.length == 1 ? locks[0] : redisson.getMultiLock(locks);
    }

    private boolean tryLock(RLock lock) {
        return tryLock(lock, 0);
    }

    private boolean tryLock(RLock lock, long waitSeconds) {
        try {
            return lock.tryLock(waitSeconds, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void unlock(RLock lock) {
        try {
            lock.unlock();
        } catch (IllegalMonitorStateException e) {
            log.warn("[SeatHold] lock already released (lease expired): {}", e.getMessage());
        }
    }

    private ApiException seatTaken(SeatSelection s) {
        return new ApiException(ErrorCode.SEAT_ALREADY_TAKEN,
                "좌석 " + s.getCarNo() + "호차 " + s.getSeatNo() + "은(는) 이미 선점된 좌석입니다.");
    }

    private List<String> seatKeys(Long scheduleSeq, List<SeatSelection> seats) {
        List<String> keys = new ArrayList<>(seats.size());
        for (SeatSelection s : seats) {
            keys.add(seatKey(scheduleSeq, s));
        }
        return keys;
    }

    private String seatKey(Long scheduleSeq, SeatSelection s) {
        return HELD_SEAT + scheduleSeq + ":" + member(s);
    }

    private String userKey(Long scheduleSeq, String userId) {
        return HELD_USER + scheduleSeq + ":" + userId;
    }

    private String member(SeatSelection s) {
        return s.getCarNo() + ":" + s.getSeatNo();
    }

    private String expiryMember(Long scheduleSeq, SeatSelection s) {
        return scheduleSeq + ":" + member(s);
    }

    private LocalDateTime toLocalDateTime(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
    }
}
