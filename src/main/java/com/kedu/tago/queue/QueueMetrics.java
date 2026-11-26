package com.kedu.tago.queue;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 대기열 메트릭 (Prometheus: /actuator/prometheus).
 *
 * tago_queue_waiting{schedule}       대기 인원 (Gauge)
 * tago_queue_active{schedule}        입장 인원 (Gauge)
 * tago_queue_admitted_total          입장시킨 인원 (Counter)
 *
 * 대기자가 사라진 뒤에도 입장 인원은 한동안 변하므로(결제·이탈), 마지막으로 활동이 있던 운행은
 * IDLE_MS 동안 계속 갱신하고, 대기·입장이 모두 0 이 되면 추적을 멈춘다.
 */
@Component
public class QueueMetrics {

    private static final long IDLE_MS = 10 * 60 * 1000L;

    private final MeterRegistry registry;
    private final Counter admitted;
    private final Map<String, AtomicLong> waiting = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> active = new ConcurrentHashMap<>();
    private final Map<String, Long> lastActivity = new ConcurrentHashMap<>();

    public QueueMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.admitted = Counter.builder("tago.queue.admitted")
                .description("대기열에서 입장시킨 인원")
                .register(registry);
    }

    public void admitted(int count) {
        if (count > 0) {
            admitted.increment(count);
        }
    }

    /** 이번 회차에 갱신할 운행: 지금 대기자가 있는 운행 + 최근 활동이 있던 운행 */
    public Set<String> schedulesToRefresh(Set<String> waitingSchedules) {
        long now = System.currentTimeMillis();
        waitingSchedules.forEach(s -> lastActivity.put(s, now));
        lastActivity.entrySet().removeIf(e -> now - e.getValue() > IDLE_MS
                && gauge(waiting, "tago.queue.waiting", e.getKey()).get() == 0
                && gauge(active, "tago.queue.active", e.getKey()).get() == 0);
        return Set.copyOf(lastActivity.keySet());
    }

    public void update(String scheduleSeq, long waitingCount, long activeCount) {
        gauge(waiting, "tago.queue.waiting", scheduleSeq).set(waitingCount);
        gauge(active, "tago.queue.active", scheduleSeq).set(activeCount);
        if (waitingCount > 0 || activeCount > 0) {
            lastActivity.put(scheduleSeq, System.currentTimeMillis());
        }
    }

    private AtomicLong gauge(Map<String, AtomicLong> values, String name, String scheduleSeq) {
        return values.computeIfAbsent(scheduleSeq, s -> {
            AtomicLong value = new AtomicLong();
            Gauge.builder(name, value, AtomicLong::get).tag("schedule", s).register(registry);
            return value;
        });
    }
}
