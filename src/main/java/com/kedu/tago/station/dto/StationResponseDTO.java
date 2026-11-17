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

    private String nodeid;
    private String nodename;
    private Integer citycode;
    private String cityname;
    private String node_address;
}
