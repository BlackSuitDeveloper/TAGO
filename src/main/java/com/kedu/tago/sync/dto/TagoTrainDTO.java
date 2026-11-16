package com.kedu.tago.sync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagoTrainDTO {
    private int trainno;
    private String vehiclekndnm;
    private String lineCategory;
}
