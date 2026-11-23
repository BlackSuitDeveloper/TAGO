package com.kedu.tago.schedule.dto;

import com.kedu.tago.common.paging.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ScheduleSearchRequestDTO extends PageParam {

    private String depStation;
    private String arrStation;
    private String depNodeId;
    private String arrNodeId;
    private String date;
    private String keyword;
    private String filter = "ALL";
    private Boolean upcoming;

    public String getEffectiveDepStation() {
        return (depStation != null && !depStation.isBlank()) ? depStation : depNodeId;
    }

    public String getEffectiveArrStation() {
        return (arrStation != null && !arrStation.isBlank()) ? arrStation : arrNodeId;
    }

    public String getNormalizedFilter() {
        return (filter != null && !filter.isBlank()) ? filter.toUpperCase() : "ALL";
    }
}
