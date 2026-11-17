package com.kedu.tago.schedule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleDTO {

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
}
