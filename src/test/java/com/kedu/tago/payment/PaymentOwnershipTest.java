package com.kedu.tago.payment;

import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.payment.dto.PaymentDTO;
import com.kedu.tago.payment.dto.PaymentRequestDTO;
import com.kedu.tago.payment.dto.PaymentResponseDTO;
import com.kedu.tago.queue.QueueService;
import com.kedu.tago.reservation.ReservationMapper;
import com.kedu.tago.reservation.dto.ReservationDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 결제 소유권 검증 테스트 (타인 예약으로 결제 확정 차단) */
class PaymentOwnershipTest {

    private final PaymentMapper paymentMapper = mock(PaymentMapper.class);
    private final ReservationMapper reservationMapper = mock(ReservationMapper.class);
    private final QueueService queueService = mock(QueueService.class);
    private final PaymentService service = new PaymentService(paymentMapper, reservationMapper, queueService);

    private ReservationDTO held(long seq, String owner) {
        return ReservationDTO.builder()
                .reservationSeq(seq).memberId(owner).status("HELD").totalPrice(59_800)
                .expiresAt(LocalDateTime.now().plusMinutes(3)).build();
    }

    private PaymentRequestDTO request(Long... ids) {
        return PaymentRequestDTO.builder().reservationIds(List.of(ids)).payMethod("CARD").totalPrice(59_800 * ids.length).build();
    }

    @Test
    @DisplayName("남의 예약으로 결제하면 403 / 2005, 결제·확정은 일어나지 않는다")
    void rejectsOtherMembersReservation() {
        when(reservationMapper.selectReservationById(1L)).thenReturn(held(1L, "owner"));

        ApiException e = assertThrows(ApiException.class, () -> service.processPayment(request(1L), "intruder"));

        assertEquals(ErrorCode.NOT_RESOURCE_OWNER, e.getErrorCode());
        verify(paymentMapper, never()).insertPayment(any(PaymentDTO.class));
        verify(reservationMapper, never()).confirmReservation(any());
    }

    @Test
    @DisplayName("여러 예약 중 하나라도 남의 것이면 전체 거부")
    void rejectsMixedOwners() {
        when(reservationMapper.selectReservationById(1L)).thenReturn(held(1L, "me"));
        when(reservationMapper.selectReservationById(2L)).thenReturn(held(2L, "someone-else"));

        ApiException e = assertThrows(ApiException.class, () -> service.processPayment(request(1L, 2L), "me"));

        assertEquals(ErrorCode.NOT_RESOURCE_OWNER, e.getErrorCode());
        verify(paymentMapper, never()).insertPayment(any(PaymentDTO.class));
    }

    @Test
    @DisplayName("로그인 정보가 없으면(null) 결제 불가")
    void rejectsMissingMember() {
        when(reservationMapper.selectReservationById(1L)).thenReturn(held(1L, "owner"));

        ApiException e = assertThrows(ApiException.class, () -> service.processPayment(request(1L), null));

        assertEquals(ErrorCode.NOT_RESOURCE_OWNER, e.getErrorCode());
    }

    @Test
    @DisplayName("본인의 예약은 결제된다")
    void allowsOwnReservation() {
        when(reservationMapper.selectReservationById(1L)).thenReturn(held(1L, "me"));
        when(reservationMapper.selectReservationById(2L)).thenReturn(held(2L, "me"));
        when(reservationMapper.confirmReservation(1L)).thenReturn(1);
        when(reservationMapper.confirmReservation(2L)).thenReturn(1);

        PaymentResponseDTO res = service.processPayment(request(1L, 2L), "me");

        assertEquals("APPROVED", res.getStatus());
        assertEquals(59_800 * 2, res.getAmount());
        verify(reservationMapper).confirmReservation(1L);
        verify(reservationMapper).confirmReservation(2L);
    }

    @Test
    @DisplayName("결제 직전 만료/중복 경합으로 조건부 UPDATE 가 0건이면 예외 발생")
    void rejectsWhenConditionalConfirmFails() {
        when(reservationMapper.selectReservationById(1L)).thenReturn(held(1L, "me"));
        when(reservationMapper.confirmReservation(1L)).thenReturn(0);

        ApiException e = assertThrows(ApiException.class, () -> service.processPayment(request(1L), "me"));
        assertEquals(ErrorCode.PAYMENT_NOT_AVAILABLE, e.getErrorCode());
    }
}
