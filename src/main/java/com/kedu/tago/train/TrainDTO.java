package com.kedu.tago.train;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainDTO {

    private Long trainno;
    private String vehiclekndnm;
    private String line_category;
}
