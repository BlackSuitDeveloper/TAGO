package com.kedu.tago.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponseDTO {
    private Long paymentSeq;
    private Long reservationId;
    private String ticketNumber;
    private String status;
    private Integer amount;
    private LocalDateTime approvedAt;
    private String message;
}
