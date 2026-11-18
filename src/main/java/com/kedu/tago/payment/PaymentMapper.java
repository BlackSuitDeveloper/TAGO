package com.kedu.tago.payment;

import com.kedu.tago.payment.dto.PaymentDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentMapper {

    int insertPayment(PaymentDTO payment);

    PaymentDTO selectPaymentByReservationSeq(@Param("reservationSeq") Long reservationSeq);

    int updatePaymentStatus(
            @Param("paymentSeq") Long paymentSeq,
            @Param("status") String status,
            @Param("failReason") String failReason
    );
}
