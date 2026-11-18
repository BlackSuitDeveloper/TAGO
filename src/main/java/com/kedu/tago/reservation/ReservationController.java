package com.kedu.tago.reservation;

import com.kedu.tago.reservation.dto.MyTicketResponseDTO;
import com.kedu.tago.reservation.dto.ReservationRequestDTO;
import com.kedu.tago.reservation.dto.ReservationResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservation")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /**
     * 좌석 선점 및 예매 생성 POST /api/reservation
     */
    @PostMapping
    public ResponseEntity<ReservationResponseDTO> holdSeats(
            @RequestBody ReservationRequestDTO requestDTO,
            HttpServletRequest request) {
        String memberId = (String) request.getAttribute("id");
        ReservationResponseDTO response = reservationService.holdSeats(requestDTO, memberId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 내 승차권 목록 조회 GET /api/reservation/me
     */
    @GetMapping("/me")
    public ResponseEntity<List<MyTicketResponseDTO>> getMyReservations(HttpServletRequest request) {
        String memberId = (String) request.getAttribute("id");
        List<MyTicketResponseDTO> list = reservationService.getMyReservations(memberId);
        return ResponseEntity.ok(list);
    }

    /**
     * 승차권 예매 취소 POST /api/reservation/{reservationId}/cancel
     */
    @PostMapping("/{reservationId}/cancel")
    public ResponseEntity<String> cancelReservation(
            @PathVariable Long reservationId,
            HttpServletRequest request) {
        String memberId = (String) request.getAttribute("id");
        reservationService.cancelReservation(reservationId, memberId);
        return ResponseEntity.ok("승차권 예매가 정상적으로 취소되었습니다.");
    }
}
