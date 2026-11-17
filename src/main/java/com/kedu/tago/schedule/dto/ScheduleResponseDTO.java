package com.kedu.tago.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleResponseDTO {

    private Long schedule_seq;
    private Long trainno;
    private String depplacename;
    private String arrplacename;
    private Timestamp depplandtime;
    private Timestamp arrplandtime;
    private Timestamp booking_open_at;
    private Long first_price;
    private Long standard_price;
    private String status;

    // JOIN 조회용 부가 정보 (역명, 열차 정보, 잔여 좌석 수)
    private String depStationName;
    private String arrStationName;
    private String vehiclekndnm;
    private String lineCategory;
    private Integer availableSeats;
}
