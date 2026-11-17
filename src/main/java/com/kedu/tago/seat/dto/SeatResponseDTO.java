package com.kedu.tago.seat.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatResponseDTO {
    private Long scheduleSeq;
    private Integer carNo;
    private Integer totalSeats;
    private Integer availableSeats;
    private List<CarSummaryDTO> carSummaryList;
    private List<SeatDTO> seatList;
}
