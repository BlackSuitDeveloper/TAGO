package com.kedu.tago.seat;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 좌석 상태 실시간 알림 (SSE). 선점·해제·결제·만료가 일어나면 같은 운행을 보고 있는 화면에 알린다.
 * 연결은 서버 메모리에 있으므로 서버 1대 기준이다.
 */
@Component
public class SeatEventPublisher {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L; // 30분

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** 운행의 좌석 알림 구독 */
    public SseEmitter subscribe(Long scheduleSeq) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> list = emitters.computeIfAbsent(scheduleSeq, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        emitter.onCompletion(() -> list.remove(emitter));
        emitter.onTimeout(() -> list.remove(emitter));
        emitter.onError(e -> list.remove(emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data(Map.of("message", "SSE connected for scheduleSeq " + scheduleSeq)));
        } catch (Exception e) {
            list.remove(emitter);
        }
        return emitter;
    }

    /** 좌석 상태 변경 알림 (HELD / AVAILABLE / CONFIRMED) */
    public void publish(Long scheduleSeq, Integer carNo, String seatNo, String status) {
        List<SseEmitter> list = emitters.get(scheduleSeq);
        if (list == null || list.isEmpty()) {
            return;
        }
        Map<String, Object> payload = Map.of(
                "scheduleSeq", scheduleSeq,
                "carNo", carNo,
                "seatNo", seatNo,
                "status", status
        );
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("SEAT_UPDATE").data(payload));
            } catch (Exception e) {
                list.remove(emitter);
            }
        }
    }
}
