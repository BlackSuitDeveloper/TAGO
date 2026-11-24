package com.kedu.tago.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleResponseDTO {

    private Long scheduleSeq;
    private Long trainNo;
    private String depPlaceName;
    private String arrPlaceName;
    private LocalDateTime depPlandTime;
    private LocalDateTime arrPlandTime;
    private LocalDateTime bookingOpenAt;
    private Long firstPrice;
    private Long standardPrice;
    private String status;

    // JOIN 조회용 부가 정보
    private String depStationName;
    private String arrStationName;
    private String vehicleKndNm;
    private String lineCategory;
    private Integer availableSeats;
}
