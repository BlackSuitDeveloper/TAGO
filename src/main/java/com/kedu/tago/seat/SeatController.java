package com.kedu.tago.seat;

import com.kedu.tago.queue.QueuePassService;
import org.springframework.web.bind.annotation.RequestHeader;

import com.kedu.tago.common.ApiPaths;
import com.kedu.tago.common.response.ResWrapper;
import com.kedu.tago.seat.dto.SeatResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 좌석 조회 및 실시간 SSE API */
@RestController
@RequestMapping(ApiPaths.V1 + "/seat")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;
    private final SeatEventPublisher seatEventPublisher;
    private final QueuePassService queuePassService;

    /** 좌석 목록 조회 (GET /api/v1/seat) */
    @GetMapping
    public ResWrapper<SeatResponseDTO> getSeats(
            @RequestHeader(value = "X-Queue-Token", required = false) String queueToken,
            @RequestParam("scheduleSeq") Long scheduleSeq, @RequestParam(value = "carNo", required = false) Integer carNo) {
        queuePassService.validateActive(queueToken, scheduleSeq); // 대기열을 통과한 사용자만 좌석 조회
        return ResWrapper.ok(seatService.getSeats(scheduleSeq, carNo));
    }

    /** 실시간 좌석 SSE 연결 (GET /api/v1/seat/stream), text/event-stream 이라 ResWrapper 로 감싸지 않음 */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSeats(@RequestParam("scheduleSeq") Long scheduleSeq) {
        return seatEventPublisher.subscribe(scheduleSeq);
    }
}
