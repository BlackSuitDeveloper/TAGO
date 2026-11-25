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
    /** Redis 선점 방식: 결제할 운행 일정 (선점 좌석은 서버가 찾는다) */
    private Long scheduleSeq;
    /** 왕복 등 여러 운행을 한 번에 결제할 때 (가는 편, 오는 편) */
    private List<Long> scheduleSeqs;
    /** 모의 PG 결과 (로컬 부하테스트용: FAIL 이면 승인 거절) */
    private String mockResult;
}
