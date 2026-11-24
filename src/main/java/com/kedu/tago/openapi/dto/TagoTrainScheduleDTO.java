package com.kedu.tago.openapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagoTrainScheduleDTO {
    private int trainno;
    private String traingradename;
    private String depPlaceId;
    private String depplacename;
    private String arrPlaceId;
    private String arrplacename;
    private String depplandtime; // yyyyMMddHHmmss
    private String arrplandtime; // yyyyMMddHHmmss
    private int adultcharge;
}
