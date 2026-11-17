package com.kedu.tago.seat.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatDTO {
    private Long seatSeq;
    private Long scheduleSeq;
    private Integer carNo;
    private String seatNo;
    private String seatGrade;
    private String status;
    private Integer version;
}
