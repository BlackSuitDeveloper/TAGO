package com.kedu.tago.queue.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueResponseDTO {
    private String queueToken;
    private Long queueRank;
    private Boolean isAvailable;
    private String message;
}
