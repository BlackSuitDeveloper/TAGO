package com.kedu.tago.seat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatStatusUpdateDTO {
    private Long scheduleSeq;
    private Integer carNo;
    private String seatNo;
    private String currentStatus;
    private String newStatus;
}
