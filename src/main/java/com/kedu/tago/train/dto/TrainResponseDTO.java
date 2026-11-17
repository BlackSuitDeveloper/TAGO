package com.kedu.tago.train.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainResponseDTO {

    private Long trainno;
    private String vehiclekndnm;
    private String line_category;
}
