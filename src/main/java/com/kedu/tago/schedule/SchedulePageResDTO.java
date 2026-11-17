package com.kedu.tago.schedule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchedulePageResDTO {

    private List<ScheduleDTO> schedules;
    private int page;
    private int size;
    private long totalCount;
    private int totalPages;
}
