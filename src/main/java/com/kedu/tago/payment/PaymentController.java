package com.kedu.tago.payment;

import com.kedu.tago.payment.dto.PaymentRequestDTO;
import com.kedu.tago.payment.dto.PaymentResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 결제 및 발권 확정 POST /api/payment
     */
    @PostMapping
    public ResponseEntity<PaymentResponseDTO> processPayment(
            @RequestBody PaymentRequestDTO requestDTO,
            HttpServletRequest request) {
        String memberId = (String) request.getAttribute("id");
        PaymentResponseDTO response = paymentService.processPayment(requestDTO, memberId);
        return ResponseEntity.ok(response);
    }
}
