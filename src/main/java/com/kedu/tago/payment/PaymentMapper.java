package com.kedu.tago.payment;

import com.kedu.tago.payment.dto.PaymentDTO;
import org.apache.ibatis.annotations.Mapper;

/** 결제 매퍼 */
@Mapper
public interface PaymentMapper {

    /** 결제 내역 저장 */
    int insertPayment(PaymentDTO payment);

}
