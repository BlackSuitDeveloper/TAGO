package com.kedu.tago.station.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationResponseDTO {

    private String nodeId;
    private String nodeName;
    private Integer cityCode;
    private String cityName;
    private String nodeAddress;
}
