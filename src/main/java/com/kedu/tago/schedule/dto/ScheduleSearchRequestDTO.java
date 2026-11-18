package com.kedu.tago.schedule.dto;

import lombok.Data;

@Data
public class ScheduleSearchRequestDTO {

    private String depStation;
    private String arrStation;
    private String depNodeId;
    private String arrNodeId;
    private String date;
    private String keyword;
    private String filter = "ALL";
    private int page = 1;
    private int size = 10;

    public String getEffectiveDepStation() {
        return (depStation != null && !depStation.isBlank()) ? depStation : depNodeId;
    }

    public String getEffectiveArrStation() {
        return (arrStation != null && !arrStation.isBlank()) ? arrStation : arrNodeId;
    }

    public String getNormalizedFilter() {
        return (filter != null && !filter.isBlank()) ? filter.toUpperCase() : "ALL";
    }

    public int getSafePage() {
        return Math.max(page, 1);
    }

    public int getSafeSize() {
        return size <= 0 ? 10 : Math.min(size, 100);
    }

    public int getOffset() {
        return (getSafePage() - 1) * getSafeSize();
    }
}
