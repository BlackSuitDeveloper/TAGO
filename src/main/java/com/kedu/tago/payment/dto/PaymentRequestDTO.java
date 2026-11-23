package com.kedu.tago.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequestDTO {
    private Long reservationId;
    private List<Long> reservationIds;
    private String payMethod;
    private Integer totalPrice;
}
