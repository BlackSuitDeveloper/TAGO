package com.kedu.tago.sync.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleInsertDTO {
    private Long scheduleSeq;
    private int trainno;
    private String depNodeId;
    private String arrNodeId;
    private LocalDateTime depTime;
    private LocalDateTime arrTime;
    private LocalDateTime bookingOpenAt;
    private int firstPrice;
    private int standardPrice;
}
