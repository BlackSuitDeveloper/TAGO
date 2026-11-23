package com.kedu.tago;

import com.kedu.tago.queue.QueueService;
import com.kedu.tago.queue.dto.QueueResponseDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

public class QueueServiceTest {

    private static final Long TEST_SCHEDULE_SEQ = 999999L;
    private static final String QUEUE_KEY = "tago:queue:" + TEST_SCHEDULE_SEQ;
    private static final String ACTIVE_KEY = "tago:queue:active:" + TEST_SCHEDULE_SEQ;
    private static final String ADMITTED_SET_KEY = "tago:queue:admitted";
    private static final String SCHEDULE_SET_KEY = "tago:queue:schedules";

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private QueueService queueService;
    private final List<String> createdTokens = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory("10.5.4.20", 6379);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        queueService = new QueueService(redisTemplate);

        cleanupKeys();
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
            redisTemplate.opsForSet().remove(ADMITTED_SET_KEY, createdTokens.toArray());
            for (String token : createdTokens) {
                redisTemplate.delete("tago:queue:meta:" + token);
            }
            createdTokens.clear();
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

        // 2단계: 기존 입장자 중 30명이 5분 TTL 만료되었다고 시뮬레이션
        Set<ZSetOperations.TypedTuple<String>> first30Active = redisTemplate.opsForZSet().rangeWithScores(ACTIVE_KEY, 0, 29);
        assertNotNull(first30Active);
        assertEquals(30, first30Active.size());
        long expiredScore = System.currentTimeMillis() - 1000L;
        for (ZSetOperations.TypedTuple<String> entry : first30Active) {
            if (entry.getValue() != null) {
                redisTemplate.opsForZSet().add(ACTIVE_KEY, entry.getValue(), expiredScore);
            }
        }

        // 대기열 1번(가장 먼저 줄 선 대기자) 확인을 위해 ZSet 최소 score 토큰 확보
        Set<String> nextInLine = redisTemplate.opsForZSet().range(QUEUE_KEY, 0, 0);
        assertNotNull(nextInLine);
        assertFalse(nextInLine.isEmpty());
        String firstWaitingToken = nextInLine.iterator().next();

        // 3단계: 스케줄러 실행 (만료 30명 제거 -> 대기열 상위 30명 순차 입장)
        queueService.scheduleAdmitQueue();

        QueueResponseDTO promotedCheck = queueService.checkQueueRank(firstWaitingToken);
        Long activeAfterSchedule = redisTemplate.opsForZSet().zCard(ACTIVE_KEY);

        // 남은 대기자 중 1등 토큰 확인
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
}
