package com.kedu.tago;

import com.kedu.tago.common.exception.ApiException;
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

/** Redis 대기열 진입/입장 테스트 (실 Redis 연결 필요) */
public class QueueServiceTest {

    private static final Long TEST_SCHEDULE_SEQ = 999999L;
    private static final String SCHEDULE = String.valueOf(TEST_SCHEDULE_SEQ);
    private static final String QUEUE_KEY = QueueService.QUEUE_KEY_PREFIX + TEST_SCHEDULE_SEQ;

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private QueueService queueService;
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
        queueService = new QueueService(redisTemplate); // max-active 200, batch-size 30 (기본값)

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
        deleteKeys(QueueService.ACTIVE_KEY_PREFIX + TEST_SCHEDULE_SEQ + ":*");
        deleteKeys(QueueService.USER_KEY_PREFIX + TEST_SCHEDULE_SEQ + ":*");
        redisTemplate.opsForSet().remove(QueueService.SCHEDULE_SET_KEY, SCHEDULE);
        for (String token : createdTokens) {
            redisTemplate.delete(QueueService.META_KEY_PREFIX + token);
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
        while ((admitted = queueService.admitWaiting(SCHEDULE)) > 0) {
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

        // 2. 스케줄러 1회는 batch-size(30)명까지만 입장
        assertEquals(30, queueService.admitWaiting(SCHEDULE));

        // 3. 계속 돌리면 정확히 200명에서 멈춘다
        admitUntilFull();
        assertEquals(200L, queueService.getActiveCount(SCHEDULE));
        assertEquals(50L, redisTemplate.opsForZSet().zCard(QUEUE_KEY));

        // 4. 입장자 30명 퇴장(TTL 만료와 같음) -> 다음 30명 입장
        Set<String> activeKeys = redisTemplate.keys(QueueService.ACTIVE_KEY_PREFIX + TEST_SCHEDULE_SEQ + ":*");
        assertNotNull(activeKeys);
        redisTemplate.delete(new ArrayList<>(activeKeys).subList(0, 30));
        String firstWaiting = redisTemplate.opsForZSet().range(QUEUE_KEY, 0, 0).iterator().next();

        assertEquals(30, admitUntilFull());
        assertEquals(200L, queueService.getActiveCount(SCHEDULE));

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
        assertThrows(ApiException.class, () ->
                queueService.validateAndExtendForHold(first.getQueueToken(), TEST_SCHEDULE_SEQ, "queueTester1", 300));

        // 2. 스케줄러 입장 후 재진입도 같은 토큰 + 입장 상태
        assertEquals(1, queueService.admitWaiting(SCHEDULE));
        QueueResponseDTO again = queueService.enterQueue(TEST_SCHEDULE_SEQ, "queueTester1");
        assertEquals(first.getQueueToken(), again.getQueueToken());
        assertTrue(again.getIsAvailable());

        // 3. 본인만 통과, 남은 시간이 짧으면 선점 시간만큼 연장
        String activeKey = QueueService.activeKey(SCHEDULE, first.getQueueToken());
        redisTemplate.expire(activeKey, Duration.ofSeconds(10));
        assertDoesNotThrow(() ->
                queueService.validateAndExtendForHold(first.getQueueToken(), TEST_SCHEDULE_SEQ, "queueTester1", 600));
        assertTrue(redisTemplate.getExpire(activeKey) > 590, "입장 시간이 600초로 연장");
        assertThrows(ApiException.class, () ->
                queueService.validateAndExtendForHold(first.getQueueToken(), TEST_SCHEDULE_SEQ, "otherUser", 300));
        assertThrows(ApiException.class, () ->
                queueService.validateAndExtendForHold(first.getQueueToken(), TEST_SCHEDULE_SEQ + 1, "queueTester1", 300));

        // 4. 유령 토큰(30초 넘게 폴링 없음)은 입장시키지 않고 정리
        String ghostToken = "ghost-token-test";
        String ghostMember = "queueTester2";
        String ghostMetaKey = QueueService.META_KEY_PREFIX + ghostToken;
        String ghostUserKey = QueueService.USER_KEY_PREFIX + TEST_SCHEDULE_SEQ + ":" + ghostMember;
        long oldTime = System.currentTimeMillis() - 120_000L;
        createdTokens.add(ghostToken);
        redisTemplate.opsForHash().putAll(ghostMetaKey, Map.of(
                "memberId", ghostMember,
                "scheduleSeq", SCHEDULE,
                "lastSeen", String.valueOf(oldTime)
        ));
        redisTemplate.opsForValue().set(ghostUserKey, ghostToken);
        redisTemplate.opsForZSet().add(QUEUE_KEY, ghostToken, oldTime);
        redisTemplate.opsForSet().add(QueueService.SCHEDULE_SET_KEY, SCHEDULE);

        assertEquals(0, queueService.admitWaiting(SCHEDULE));
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(QueueService.activeKey(SCHEDULE, ghostToken))));
        assertNull(redisTemplate.opsForZSet().score(QUEUE_KEY, ghostToken), "대기열에서 빠짐");
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(ghostMetaKey)));
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(ghostUserKey)));
        assertFalse(Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(QueueService.SCHEDULE_SET_KEY, SCHEDULE)),
                "대기자가 없으면 운행 목록에서 빠짐");

        // 5. 슬롯 반납 후에는 입장 키가 없어 선점 불가
        queueService.releaseSlot(TEST_SCHEDULE_SEQ, "queueTester1");
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(activeKey)));
        assertThrows(ApiException.class, () ->
                queueService.validateAndExtendForHold(first.getQueueToken(), TEST_SCHEDULE_SEQ, "queueTester1", 300));
    }
}
