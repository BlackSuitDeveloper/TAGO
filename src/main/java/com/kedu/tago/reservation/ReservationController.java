package com.kedu.tago.reservation;

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
@RequestMapping("/reservation")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /** 좌석 선점 및 예매 생성 (POST /api/reservation) */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResWrapper<ReservationResponseDTO> holdSeats(
            @RequestBody ReservationRequestDTO requestDTO,
            HttpServletRequest request) {

        String memberId = (String) request.getAttribute("id");
        return ResWrapper.ok(reservationService.holdSeats(requestDTO, memberId));
    }

    /** 내 승차권 목록 조회 (GET /api/reservation/me) */
    @GetMapping("/me")
    public ResWrapper<List<MyTicketResponseDTO>> getMyReservations(HttpServletRequest request) {
        String memberId = (String) request.getAttribute("id");
        return ResWrapper.list(reservationService.getMyReservations(memberId));
    }

    /** 승차권 예매 취소 (POST /api/reservation/{reservationId}/cancel) */
    @PostMapping("/{reservationId}/cancel")
    public ResWrapper<Void> cancelReservation(
            @PathVariable Long reservationId,
            HttpServletRequest request) {

        String memberId = (String) request.getAttribute("id");
        reservationService.cancelReservation(reservationId, memberId);
        return ResWrapper.done("승차권 예매가 정상적으로 취소되었습니다.");
    }
}
