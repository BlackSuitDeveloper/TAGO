package com.kedu.tago.seat;

import com.kedu.tago.seat.dto.SeatResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<SeatResponseDTO> getSeats(@RequestParam Long scheduleSeq, @RequestParam(required = false) Integer carNo) {
        // 좌석 목록 조회 GET /api/seat?scheduleSeq={scheduleSeq}&carNo={carNo}
        SeatResponseDTO response = seatService.getSeats(scheduleSeq, carNo);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSeats(@RequestParam Long scheduleSeq) {
        // 실시간 좌석 SSE 연결 GET /api/seat/stream?scheduleSeq={scheduleSeq}
        return seatService.subscribeSeatStream(scheduleSeq);
    }
}
