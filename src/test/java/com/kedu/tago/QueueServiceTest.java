package com.kedu.tago;

import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.queue.QueueAdmissionService;
import com.kedu.tago.queue.QueueKeys;
import com.kedu.tago.queue.QueuePassService;
import com.kedu.tago.queue.QueueService;
import com.kedu.tago.queue.dto.QueueResponseDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Redis 대기열 줄 서기 → 스케줄러 입장 → 입장권 검증·반납 테스트 (실 Redis 연결 필요) */
public class QueueServiceTest {

    private static final Long TEST_SCHEDULE_SEQ = 999999L;
    private static final String SCHEDULE = String.valueOf(TEST_SCHEDULE_SEQ);
    private static final String QUEUE_KEY = QueueKeys.WAIT_PREFIX + TEST_SCHEDULE_SEQ;

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private QueueService queueService;
    private QueueAdmissionService admission;
    private QueuePassService pass;
    private final List<String> createdTokens = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() {
        // 앱과 같은 환경변수로 Redis 접속 (없으면 localhost:6379)
        String host = envOrDefault("REDIS_DB_URL", "localhost");
        int port = Integer.parseInt(envOrDefault("REDIS_DB_PORT", "6379"));
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        String password = System.getenv("REDIS_DB_PASSWORD");
        if (password != null && !password.isBlank()) {
            config.setPassword(password);
        }
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        queueService = new QueueService(redisTemplate);
        admission = new QueueAdmissionService(redisTemplate); // max-active 200, batch-size 30 (기본값)
        pass = new QueuePassService(redisTemplate);

        cleanupKeys();
    }

    private static String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    @AfterEach
    void tearDown() {
        cleanupKeys();
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    private void cleanupKeys() {
        if (redisTemplate == null) return;
        redisTemplate.delete(QUEUE_KEY);
        deleteKeys(QueueKeys.ACTIVE_PREFIX + TEST_SCHEDULE_SEQ + ":*");
        deleteKeys(QueueKeys.USER_PREFIX + TEST_SCHEDULE_SEQ + ":*");
        redisTemplate.opsForSet().remove(QueueKeys.SCHEDULES, SCHEDULE);
        for (String token : createdTokens) {
            redisTemplate.delete(QueueKeys.META_PREFIX + token);
        }
        createdTokens.clear();
    }

    private void deleteKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /** 스케줄러가 여러 번 도는 것처럼, 더 입장할 사람이 없을 때까지 입장 처리 */
    private int admitUntilFull() {
        int total = 0;
        int admitted;
        while ((admitted = admission.admitWaiting(SCHEDULE)) > 0) {
            total += admitted;
        }
        return total;
    }

    @Test
    @DisplayName("250명 동시 진입 -> 전원 대기열(순번 중복 없음) -> 스케줄러가 200명 입장 -> 30명 퇴장 후 다음 30명 입장")
    void concurrentEnterThenSchedulerAdmits() throws InterruptedException {
        int totalUsers = 250;
        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalUsers);
        List<QueueResponseDTO> responses = Collections.synchronizedList(new ArrayList<>());

        try {
            for (int i = 1; i <= totalUsers; i++) {
                final String memberId = "queueTester" + i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        QueueResponseDTO resp = queueService.enterQueue(TEST_SCHEDULE_SEQ, memberId);
                        responses.add(resp);
                        createdTokens.add(resp.getQueueToken());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown();
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdown();
        }

        // 1. 진입만으로는 아무도 입장하지 않는다 (입장은 스케줄러 몫)
        assertEquals(totalUsers, responses.size());
        assertTrue(responses.stream().noneMatch(r -> Boolean.TRUE.equals(r.getIsAvailable())));
        assertEquals(totalUsers, redisTemplate.opsForZSet().zCard(QUEUE_KEY));
        Set<String> tokens = new HashSet<>();
        responses.forEach(r -> tokens.add(r.getQueueToken()));
        assertEquals(totalUsers, tokens.size(), "회원마다 다른 토큰");
        // 진입 응답의 순번은 동시 진입 중 계산된 값이므로, 다 들어온 뒤 다시 조회해 1~250 이 한 번씩인지 본다
        Set<Long> ranks = new HashSet<>();
        for (String token : tokens) {
            ranks.add(queueService.checkQueueRank(token).getQueueRank());
        }
        assertEquals(totalUsers, ranks.size(), "순번 중복 없음");
        assertEquals(1L, Collections.min(ranks));
        assertEquals((long) totalUsers, Collections.max(ranks));

        // 2. 스케줄러 1회는 batch-size(30)명까지만 입장
        assertEquals(30, admission.admitWaiting(SCHEDULE));

        // 3. 계속 돌리면 정확히 200명에서 멈춘다
        admitUntilFull();
        assertEquals(200L, admission.getActiveCount(SCHEDULE));
        assertEquals(50L, redisTemplate.opsForZSet().zCard(QUEUE_KEY));

        // 4. 입장자 30명 퇴장(TTL 만료와 같음) -> 다음 30명 입장
        Set<String> activeKeys = redisTemplate.keys(QueueKeys.ACTIVE_PREFIX + TEST_SCHEDULE_SEQ + ":*");
        assertNotNull(activeKeys);
        redisTemplate.delete(new ArrayList<>(activeKeys).subList(0, 30));
        String firstWaiting = redisTemplate.opsForZSet().range(QUEUE_KEY, 0, 0).iterator().next();

        assertEquals(30, admitUntilFull());
        assertEquals(200L, admission.getActiveCount(SCHEDULE));

        QueueResponseDTO promoted = queueService.checkQueueRank(firstWaiting);
        assertTrue(promoted.getIsAvailable(), "1번 대기자는 입장 상태");
        assertEquals(0L, promoted.getQueueRank());

        String nextWaiting = redisTemplate.opsForZSet().range(QUEUE_KEY, 0, 0).iterator().next();
        QueueResponseDTO stillWaiting = queueService.checkQueueRank(nextWaiting);
        assertFalse(stillWaiting.getIsAvailable());
        assertEquals(1L, stillWaiting.getQueueRank(), "남은 첫 대기자는 1번");
    }

    @Test
    @DisplayName("멱등 진입 -> 입장 -> 선점 검증·연장 -> 유령 토큰 정리 -> 슬롯 반납")
    void fullQueueLifecycle() {
        // 1. 같은 회원+열차는 같은 토큰
        QueueResponseDTO first = queueService.enterQueue(TEST_SCHEDULE_SEQ, "queueTester1");
        QueueResponseDTO second = queueService.enterQueue(TEST_SCHEDULE_SEQ, "queueTester1");
        createdTokens.add(first.getQueueToken());
        assertEquals(first.getQueueToken(), second.getQueueToken());
        assertFalse(first.getIsAvailable());
        assertEquals(1L, first.getQueueRank());

        // 입장 전에는 선점 불가
        ApiException notAdmitted = assertThrows(ApiException.class, () ->
                pass.validateAndExtendForHold(first.getQueueToken(), TEST_SCHEDULE_SEQ, "queueTester1", 300));
        assertEquals(ErrorCode.QUEUE_NOT_ADMITTED, notAdmitted.getErrorCode());

        // 2. 스케줄러 입장 후 재진입도 같은 토큰 + 입장 상태
        assertEquals(1, admission.admitWaiting(SCHEDULE));
        QueueResponseDTO again = queueService.enterQueue(TEST_SCHEDULE_SEQ, "queueTester1");
        assertEquals(first.getQueueToken(), again.getQueueToken());
        assertTrue(again.getIsAvailable());

        // 3. 본인만 통과, 남은 시간이 짧으면 선점 시간만큼 연장
        String activeKey = QueueKeys.active(SCHEDULE, first.getQueueToken());
        redisTemplate.expire(activeKey, Duration.ofSeconds(10));
        assertDoesNotThrow(() ->
                pass.validateAndExtendForHold(first.getQueueToken(), TEST_SCHEDULE_SEQ, "queueTester1", 600));
        assertTrue(redisTemplate.getExpire(activeKey) > 590, "입장 시간이 600초로 연장");
        assertThrows(ApiException.class, () ->
                pass.validateAndExtendForHold(first.getQueueToken(), TEST_SCHEDULE_SEQ, "otherUser", 300));
        assertThrows(ApiException.class, () ->
                pass.validateAndExtendForHold(first.getQueueToken(), TEST_SCHEDULE_SEQ + 1, "queueTester1", 300));

        // 4. 유령 토큰(120초 넘게 폴링 없음)은 입장시키지 않고 정리
        String ghostToken = "ghost-token-test";
        String ghostMember = "queueTester2";
        String ghostMetaKey = QueueKeys.META_PREFIX + ghostToken;
        String ghostUserKey = QueueKeys.USER_PREFIX + TEST_SCHEDULE_SEQ + ":" + ghostMember;
        long oldTime = System.currentTimeMillis() - 600_000L; // 10분 전 (유령 기준 120초 초과)
        createdTokens.add(ghostToken);
        redisTemplate.opsForHash().putAll(ghostMetaKey, Map.of(
                "memberId", ghostMember,
                "scheduleSeq", SCHEDULE,
                "lastSeen", String.valueOf(oldTime)
        ));
        redisTemplate.opsForValue().set(ghostUserKey, ghostToken);
        redisTemplate.opsForZSet().add(QUEUE_KEY, ghostToken, oldTime);
        redisTemplate.opsForSet().add(QueueKeys.SCHEDULES, SCHEDULE);

        assertEquals(0, admission.admitWaiting(SCHEDULE));
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(QueueKeys.active(SCHEDULE, ghostToken))));
        assertNull(redisTemplate.opsForZSet().score(QUEUE_KEY, ghostToken), "대기열에서 빠짐");
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(ghostMetaKey)));
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(ghostUserKey)));
        assertFalse(Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(QueueKeys.SCHEDULES, SCHEDULE)),
                "대기자가 없으면 운행 목록에서 빠짐");

        // 5. 슬롯 반납 후에는 입장 키가 없어 선점 불가
        pass.releaseSlot(TEST_SCHEDULE_SEQ, "queueTester1");
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(activeKey)));
        assertThrows(ApiException.class, () ->
                pass.validateAndExtendForHold(first.getQueueToken(), TEST_SCHEDULE_SEQ, "queueTester1", 300));
    }

    @Test
    @DisplayName("같은 회원이 동시에 10번 진입해도 토큰은 하나")
    void concurrentEnterSameMemberGetsOneToken() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(10);
        Set<String> tokens = Collections.synchronizedSet(new HashSet<>());
        try {
            for (int i = 0; i < 10; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        tokens.add(queueService.enterQueue(TEST_SCHEDULE_SEQ, "queueTester1").getQueueToken());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdown();
        }
        createdTokens.addAll(tokens);

        assertEquals(1, tokens.size(), "토큰은 하나");
        assertEquals(1L, redisTemplate.opsForZSet().zCard(QUEUE_KEY), "대기열에도 한 번만");
    }

    @Test
    @DisplayName("대기자가 남아 있는데 운행이 스케줄러 목록에서 빠져도, 폴링하면 다시 들어가 입장된다")
    void pollingRestoresScheduleSet() {
        QueueResponseDTO entered = queueService.enterQueue(TEST_SCHEDULE_SEQ, "queueTester1");
        createdTokens.add(entered.getQueueToken());
        redisTemplate.opsForSet().remove(QueueKeys.SCHEDULES, SCHEDULE); // 경합으로 빠진 상황

        queueService.checkQueueRank(entered.getQueueToken());

        assertTrue(admission.getWaitingSchedules().contains(SCHEDULE));
        assertEquals(1, admission.admitWaiting(SCHEDULE));
        assertTrue(queueService.checkQueueRank(entered.getQueueToken()).getIsAvailable());
    }

    @Test
    @DisplayName("입장 처리 직전에 이탈(슬롯 반납)한 토큰은 입장 키를 남기지 않는다")
    void releasedBeforeAdmitLeavesNoActiveKey() {
        QueueResponseDTO entered = queueService.enterQueue(TEST_SCHEDULE_SEQ, "queueTester1");
        createdTokens.add(entered.getQueueToken());
        // 이탈 처리 중 메타만 먼저 지워진 상태 (대기열에는 아직 남아 있음)
        redisTemplate.delete(QueueKeys.META_PREFIX + entered.getQueueToken());

        assertEquals(0, admission.admitWaiting(SCHEDULE));
        assertEquals(0L, admission.getActiveCount(SCHEDULE));
    }
}
