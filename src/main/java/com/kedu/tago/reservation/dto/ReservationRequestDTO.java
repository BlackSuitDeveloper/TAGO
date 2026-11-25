package com.kedu.tago.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationRequestDTO {
    private Long scheduleSeq;
    private Integer carNo;
    private List<String> seatList;
    private List<SeatSelection> seats;
    private Integer count;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatSelection {
        private Integer carNo;
        private String seatNo;
    }
}
