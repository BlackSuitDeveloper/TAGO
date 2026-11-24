package com.kedu.tago.seat;

import com.kedu.tago.common.response.ResWrapper;
import com.kedu.tago.seat.dto.SeatResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/seat")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @GetMapping
    public ResWrapper<SeatResponseDTO> getSeats(@RequestParam Long scheduleSeq, @RequestParam(required = false) Integer carNo) {
        // 좌석 목록 조회 GET /api/seat?scheduleSeq={scheduleSeq}&carNo={carNo}
        return ResWrapper.ok(seatService.getSeats(scheduleSeq, carNo));
    }

    // SSE 스트림은 이벤트 형식(text/event-stream)이라 ResWrapper 로 감싸지 않는다
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSeats(@RequestParam Long scheduleSeq) {
        // 실시간 좌석 SSE 연결 GET /api/seat/stream?scheduleSeq={scheduleSeq}
        return seatService.subscribeSeatStream(scheduleSeq);
    }
}
