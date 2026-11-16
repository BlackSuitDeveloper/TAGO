package com.kedu.tago.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncResultDTO {
    private String syncType;
    private String targetDate;
    private int stationCount;
    private int trainCount;
    private int insertedScheduleCount;
    private int skippedScheduleCount;
    private int insertedSeatCount;
    private LocalDateTime executedAt;
    private String message;
}
