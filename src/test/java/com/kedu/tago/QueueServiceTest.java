package com.kedu.tago;

import com.kedu.tago.queue.QueueService;
import com.kedu.tago.queue.dto.QueueResponseDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/** Redis 대기열 입장/승격 테스트 (실 Redis 연결 필요) */
public class QueueServiceTest {

    private static final Long TEST_SCHEDULE_SEQ = 999999L;
    private static final String QUEUE_KEY = "tago:queue:wait:" + TEST_SCHEDULE_SEQ;
    private static final String ACTIVE_KEY = "tago:queue:active:" + TEST_SCHEDULE_SEQ;
    private static final String SCHEDULE_SET_KEY = "tago:queue:schedules";

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
        queueService = new QueueService(redisTemplate);

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
        redisTemplate.delete(ACTIVE_KEY);
        redisTemplate.opsForSet().remove(SCHEDULE_SET_KEY, String.valueOf(TEST_SCHEDULE_SEQ));
        if (!createdTokens.isEmpty()) {
            for (String token : createdTokens) {
                redisTemplate.delete("tago:queue:meta:" + token);
            }
            createdTokens.clear();
        }
        for (int i = 1; i <= 250; i++) {
            redisTemplate.delete("tago:queue:user:" + TEST_SCHEDULE_SEQ + ":queueTester" + i);
        }
    }

    @Test
    @DisplayName("Redis 대기열 통합 테스트: 250명 동시 진입 -> 200명 즉시 입장, 50명 대기 -> 만료 후 30명 순차 입장")
    void testRedisQueueLimitAndScheduledAdmit() throws InterruptedException {
        int totalUsers = 250;
        ExecutorService executor = Executors.newCachedThreadPool();
        CountDownLatch readyLatch = new CountDownLatch(totalUsers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalUsers);

        List<QueueResponseDTO> admittedResponses = Collections.synchronizedList(new ArrayList<>());
        List<QueueResponseDTO> waitingResponses = Collections.synchronizedList(new ArrayList<>());

        try {
            for (int i = 1; i <= totalUsers; i++) {
                final String memberId = "queueTester" + i;
                executor.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();

                        QueueResponseDTO resp = queueService.enterQueue(TEST_SCHEDULE_SEQ, memberId);
                        createdTokens.add(resp.getQueueToken());
                        if (Boolean.TRUE.equals(resp.getIsAvailable())) {
                            admittedResponses.add(resp);
                        } else {
                            waitingResponses.add(resp);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await();
            startLatch.countDown();
            doneLatch.await();
        } finally {
            executor.shutdown();
        }

        Long activeCount = redisTemplate.opsForZSet().zCard(ACTIVE_KEY);
        Long waitingCount = redisTemplate.opsForZSet().zCard(QUEUE_KEY);

        System.out.println("===================================================");
        System.out.println("[1단계: 250명 동시 진입 결과]");
        System.out.println("즉시 입장 허용: " + admittedResponses.size() + "명 (activeCount=" + activeCount + ")");
        System.out.println("대기열 대기 중: " + waitingResponses.size() + "명 (queueSize=" + waitingCount + ")");
        System.out.println("===================================================");

        assertEquals(200, admittedResponses.size(), "정확히 200명까지 즉시 입장되어야 합니다.");
        assertEquals(50, waitingResponses.size(), "나머지 50명은 대기열에 들어가야 합니다.");
        assertEquals(200L, activeCount);

        // 기존 입장자 30명이 TTL 만료된 상태로 시뮬레이션
        Set<ZSetOperations.TypedTuple<String>> first30Active = redisTemplate.opsForZSet().rangeWithScores(ACTIVE_KEY, 0, 29);
        assertNotNull(first30Active);
        assertEquals(30, first30Active.size());
        long expiredScore = System.currentTimeMillis() - 1000L;
        for (ZSetOperations.TypedTuple<String> entry : first30Active) {
            if (entry.getValue() != null) {
                redisTemplate.opsForZSet().add(ACTIVE_KEY, entry.getValue(), expiredScore);
            }
        }

        // 1번 대기자 토큰 확보 (ZSet 최소 score)
        Set<String> nextInLine = redisTemplate.opsForZSet().range(QUEUE_KEY, 0, 0);
        assertNotNull(nextInLine);
        assertFalse(nextInLine.isEmpty());
        String firstWaitingToken = nextInLine.iterator().next();

        // 스케줄러 실행 (만료 30명 제거 후 대기열 상위 30명 입장)
        queueService.scheduleAdmitQueue();

        QueueResponseDTO promotedCheck = queueService.checkQueueRank(firstWaitingToken);
        Long activeAfterSchedule = redisTemplate.opsForZSet().zCard(ACTIVE_KEY);

        // 남은 대기자 중 1번 토큰
        Set<String> remainingFirst = redisTemplate.opsForZSet().range(QUEUE_KEY, 0, 0);
        assertNotNull(remainingFirst);
        String remainingFirstToken = remainingFirst.iterator().next();
        QueueResponseDTO stillWaitingCheck = queueService.checkQueueRank(remainingFirstToken);

        System.out.println("[2단계: 30명 TTL 만료 후 스케줄러 1회 실행 결과]");
        System.out.println("승격된 1번 대기자 상태: isAvailable=" + promotedCheck.getIsAvailable() + ", rank=" + promotedCheck.getQueueRank());
        System.out.println("남은 다음 대기자 상태: isAvailable=" + stillWaitingCheck.getIsAvailable() + ", rank=" + stillWaitingCheck.getQueueRank());
        System.out.println("스케줄러 실행 후 활성 입장자 수: " + activeAfterSchedule + "명");
        System.out.println("===================================================");

        assertTrue(promotedCheck.getIsAvailable(), "1번 대기자는 입장 허용 상태여야 합니다.");
        assertEquals(0L, promotedCheck.getQueueRank());
        assertFalse(stillWaitingCheck.getIsAvailable(), "31번 대기자는 아직 대기 중이어야 합니다.");
        assertEquals(1L, stillWaitingCheck.getQueueRank(), "앞의 30명이 빠졌으므로 남은 첫 대기자는 1번이어야 합니다.");
        assertEquals(200L, activeAfterSchedule);
    }

    @Test
    @DisplayName("PDF 아키텍처 전체 시뮬레이션: 멱등 진입 -> 유령 토큰 스킵/userKey 삭제 -> 검증/연장/차단 -> 슬롯 반납")
    void testFullQueueLifecycleSimulation() {
        // 1. 1인 1토큰 멱등성 검증
        QueueResponseDTO first = queueService.enterQueue(TEST_SCHEDULE_SEQ, "queueTester1");
        QueueResponseDTO second = queueService.enterQueue(TEST_SCHEDULE_SEQ, "queueTester1");
        createdTokens.add(first.getQueueToken());
        assertEquals(first.getQueueToken(), second.getQueueToken(), "같은 회원+열차는 기존 토큰을 재사용해야 합니다.");
        assertTrue(first.getIsAvailable());

        // 2. VALIDATE_AND_EXTEND_SCRIPT: 본인/타인/타열차 검증
        assertDoesNotThrow(() ->
                queueService.validateAndExtendForHold(first.getQueueToken(), TEST_SCHEDULE_SEQ, "queueTester1", 300));
        assertThrows(com.kedu.tago.common.exception.ApiException.class, () ->
                queueService.validateAndExtendForHold(first.getQueueToken(), TEST_SCHEDULE_SEQ, "otherUser", 300));
        assertThrows(com.kedu.tago.common.exception.ApiException.class, () ->
                queueService.validateAndExtendForHold(first.getQueueToken(), TEST_SCHEDULE_SEQ + 1, "queueTester1", 300));

        // 3. 유령 토큰 스킵 및 userKey 정리 검증
        String ghostToken = "ghost-token-test";
        String ghostMember = "queueTester2";
        String ghostMetaKey = QueueService.META_KEY_PREFIX + ghostToken;
        String ghostUserKey = QueueService.USER_KEY_PREFIX + TEST_SCHEDULE_SEQ + ":" + ghostMember;
        long oldTime = System.currentTimeMillis() - 120_000L; // 2분 전 (30초 cutoff 초과)
        redisTemplate.opsForHash().putAll(ghostMetaKey, java.util.Map.of(
                "memberId", ghostMember,
                "scheduleSeq", String.valueOf(TEST_SCHEDULE_SEQ),
                "lastSeen", String.valueOf(oldTime)
        ));
        redisTemplate.opsForValue().set(ghostUserKey, ghostToken);
        redisTemplate.opsForZSet().add(QUEUE_KEY, ghostToken, oldTime);
        redisTemplate.opsForSet().add(SCHEDULE_SET_KEY, String.valueOf(TEST_SCHEDULE_SEQ));

        queueService.scheduleAdmitQueue();

        assertNull(redisTemplate.opsForZSet().score(ACTIVE_KEY, ghostToken), "유령 토큰은 active 로 승격되면 안 됩니다.");
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(ghostMetaKey)), "유령 토큰 meta 키가 삭제되어야 합니다.");
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(ghostUserKey)), "유령 토큰 userKey 도 함께 삭제되어야 합니다.");

        // 4. 슬롯 반납 (releaseSlot) 후 재검증 거부 확인
        queueService.releaseSlot(TEST_SCHEDULE_SEQ, "queueTester1");
        assertNull(redisTemplate.opsForZSet().score(ACTIVE_KEY, first.getQueueToken()), "반납 후 active 에서 즉시 제거되어야 합니다.");
        assertThrows(com.kedu.tago.common.exception.ApiException.class, () ->
                queueService.validateAndExtendForHold(first.getQueueToken(), TEST_SCHEDULE_SEQ, "queueTester1", 300));
    }
}
