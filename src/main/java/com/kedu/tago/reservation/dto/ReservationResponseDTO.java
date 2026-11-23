package com.kedu.tago.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponseDTO {
    private Long reservationId;
    private Long reservationSeq;
    private List<Long> reservationIds;
    private Long scheduleSeq;
    private Integer carNo;
    private List<String> seatList;
    private Integer totalPrice;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime reservedAt;
}
