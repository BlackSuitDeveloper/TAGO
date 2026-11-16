package com.kedu.tago.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagoStationDTO {
    private String nodeid;
    private String nodename;
    private String citycode;
    private String cityname;
    private String nodeAddress;
}
