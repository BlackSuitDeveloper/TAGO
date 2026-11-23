package com.kedu.tago.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyTicketResponseDTO {
    private Long reservationSeq;
    private Long scheduleSeq;
    private String trainNo;
    private String vehicleKndNm;
    private String depStation;
    private String arrStation;
    private LocalDateTime depPlandTime;
    private LocalDateTime arrPlandTime;
    private Integer carNo;
    private String seatNo;
    private String seatGrade;
    private Integer totalPrice;
    private String status;
    private String ticketNumber;
    private LocalDateTime reservedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime confirmedAt;
}
