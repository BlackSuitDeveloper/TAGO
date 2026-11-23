package com.kedu.tago.payment;

import com.kedu.tago.payment.dto.PaymentDTO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentMapper {

    int insertPayment(PaymentDTO payment);

}
