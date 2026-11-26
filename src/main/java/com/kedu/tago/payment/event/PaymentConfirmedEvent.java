package com.kedu.tago.payment.event;

import com.kedu.tago.reservation.dto.ReservationRequestDTO.SeatSelection;

import java.util.List;

/** 결제 확정 커밋 후 처리할 정보 */
public record PaymentConfirmedEvent(Long scheduleSeq, String memberId, List<SeatSelection> seats) {
}
