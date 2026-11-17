package com.kedu.tago.seat.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarSummaryDTO {
    private Integer carNo;
    private Integer totalSeats;
    private Integer availableSeats;
}
