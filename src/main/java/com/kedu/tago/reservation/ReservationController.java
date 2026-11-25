package com.kedu.tago.reservation;

import com.kedu.tago.common.ApiPaths;
import com.kedu.tago.common.response.ResWrapper;
import com.kedu.tago.reservation.dto.MyTicketResponseDTO;
import com.kedu.tago.reservation.dto.ReservationRequestDTO;
import com.kedu.tago.reservation.dto.ReservationResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 예매 API */
@RestController
@RequestMapping(ApiPaths.V1 + "/reservation")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /** 좌석 선점 및 예매 생성 (POST /api/v1/reservation) */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResWrapper<ReservationResponseDTO> holdSeats(
            @RequestHeader(value = "X-Queue-Token", required = false) String queueToken,
            @RequestBody ReservationRequestDTO requestDTO,
            HttpServletRequest request) {

        String memberId = (String) request.getAttribute("id");
        return ResWrapper.ok(reservationService.holdSeats(requestDTO, memberId, queueToken));
    }

    /** 좌석 다시 고르기: 내 선점 해제 (DELETE /api/v1/reservation/hold?scheduleSeq=) */
    @DeleteMapping("/hold")
    public ResWrapper<Void> releaseHolds(@RequestParam Long scheduleSeq, HttpServletRequest request) {
        String memberId = (String) request.getAttribute("id");
        reservationService.releaseHolds(scheduleSeq, memberId);
        return ResWrapper.done("선점한 좌석을 반납했습니다.");
    }

    /** 예매 그만두기 / 페이지 이탈(sendBeacon): 선점 해제 + 대기열 슬롯 반납 (POST /api/v1/reservation/leave?scheduleSeq=) */
    @PostMapping("/leave")
    public ResWrapper<Void> leave(@RequestParam Long scheduleSeq, HttpServletRequest request) {
        String memberId = (String) request.getAttribute("id");
        reservationService.leave(scheduleSeq, memberId);
        return ResWrapper.done("예매를 종료했습니다.");
    }

    /** 내 승차권 목록 조회 (GET /api/v1/reservation/me) */
    @GetMapping("/me")
    public ResWrapper<List<MyTicketResponseDTO>> getMyReservations(HttpServletRequest request) {
        String memberId = (String) request.getAttribute("id");
        return ResWrapper.list(reservationService.getMyReservations(memberId));
    }

    /** 승차권 예매 취소 (POST /api/v1/reservation/{reservationId}/cancel) */
    @PostMapping("/{reservationId}/cancel")
    public ResWrapper<Void> cancelReservation(
            @PathVariable Long reservationId,
            HttpServletRequest request) {

        String memberId = (String) request.getAttribute("id");
        reservationService.cancelReservation(reservationId, memberId);
        return ResWrapper.done("승차권 예매가 정상적으로 취소되었습니다.");
    }
}
