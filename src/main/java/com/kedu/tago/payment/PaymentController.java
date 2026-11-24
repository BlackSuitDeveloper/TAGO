package com.kedu.tago.payment;

import com.kedu.tago.common.ApiPaths;
import com.kedu.tago.common.response.ResWrapper;
import com.kedu.tago.payment.dto.PaymentRequestDTO;
import com.kedu.tago.payment.dto.PaymentResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 결제 API */
@RestController
@RequestMapping(ApiPaths.V1 + "/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** 결제 및 발권 확정 (POST /api/v1/payment) */
    @PostMapping
    public ResWrapper<PaymentResponseDTO> processPayment(
            @RequestBody PaymentRequestDTO requestDTO,
            HttpServletRequest request) {

        String memberId = (String) request.getAttribute("id");
        return ResWrapper.ok(paymentService.processPayment(requestDTO, memberId));
    }
}
