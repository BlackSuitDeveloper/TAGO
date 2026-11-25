package com.kedu.tago.payment;

import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.payment.dto.PaymentDTO;
import com.kedu.tago.payment.dto.PaymentRequestDTO;
import com.kedu.tago.payment.dto.PaymentResponseDTO;
import com.kedu.tago.payment.event.PaymentConfirmedEvent;
import com.kedu.tago.queue.QueuePassService;
import com.kedu.tago.reservation.ReservationMapper;
import com.kedu.tago.reservation.ReservationService;
import com.kedu.tago.reservation.dto.ReservationDTO;
import com.kedu.tago.reservation.dto.ReservationRequestDTO.SeatSelection;
import com.kedu.tago.schedule.dto.ScheduleResponseDTO;
import com.kedu.tago.seat.hold.SeatHoldService;
import com.kedu.tago.seat.SeatMapper;
import com.kedu.tago.seat.SeatEventPublisher;
import com.kedu.tago.seat.SeatTemplateGenerator;
import com.kedu.tago.seat.dto.SeatDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 결제 서비스 단위 테스트 (Redis 선점 방식 + 멱등키). DB·Redis 없이 Mockito 로만 검증 */
class PaymentServiceTest {

    private static final long S1 = 100L;  // 가는 편
    private static final long S2 = 200L;  // 오는 편
    private static final int PRICE = 54_400;
    private static final String KEY = "idem-key-1";

    private final PaymentMapper paymentMapper = mock(PaymentMapper.class);
    private final ReservationMapper reservationMapper = mock(ReservationMapper.class);
    private final ReservationService reservationService = mock(ReservationService.class);
    private final SeatHoldService seatHoldService = mock(SeatHoldService.class);
    private final SeatMapper seatMapper = mock(SeatMapper.class);
    private final SeatEventPublisher seatEventPublisher = mock(SeatEventPublisher.class);
    private final QueuePassService queuePassService = mock(QueuePassService.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final PaymentIdempotencyService idempotency = mock(PaymentIdempotencyService.class);

    private final PaymentService service = new PaymentService(paymentMapper, reservationMapper, reservationService,
            seatHoldService, seatMapper, seatEventPublisher, new SeatTemplateGenerator(), queuePassService, tx, events, idempotency);

    private final AtomicLong seq = new AtomicLong(1);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // 트랜잭션 템플릿은 콜백을 그대로 실행
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(null));
        // seat / reservation INSERT 는 성공하고 PK 를 채워 준다
        when(seatMapper.insertOccupiedSeat(any())).thenAnswer(inv -> {
            ((SeatDTO) inv.getArgument(0)).setSeatSeq(seq.getAndIncrement());
            return 1;
        });
        when(reservationMapper.insertReservation(any())).thenAnswer(inv -> {
            ((ReservationDTO) inv.getArgument(0)).setReservationSeq(seq.getAndIncrement());
            return 1;
        });
        when(reservationService.validateBookableSchedule(anyLong()))
                .thenReturn(ScheduleResponseDTO.builder().standardPrice((long) PRICE).status("SCHEDULED").build());
    }

    private static List<SeatSelection> seats(String... seatNos) {
        return java.util.Arrays.stream(seatNos).map(no -> new SeatSelection(1, no)).toList();
    }

    private static PaymentRequestDTO pay(Long... schedules) {
        return PaymentRequestDTO.builder().scheduleSeq(schedules[0]).scheduleSeqs(List.of(schedules)).payMethod("CARD").build();
    }

    @Test
    @DisplayName("로그인 정보가 없으면 결제 불가 (멱등키 처리도 시작하지 않음)")
    void rejectsMissingMember() {
        ApiException e = assertThrows(ApiException.class, () -> service.processPayment(pay(S1), null, KEY));
        assertEquals(ErrorCode.LOGIN_REQUIRED, e.getErrorCode());
        verify(idempotency, never()).begin(any(), any());
    }

    @Test
    @DisplayName("내 선점이 없으면(만료됐거나 남의 좌석) 410, 결제·확정은 일어나지 않고 멱등키는 해제")
    void rejectsWhenNoHolds() {
        when(seatHoldService.getMyHolds(S1, "me")).thenReturn(List.of());

        ApiException e = assertThrows(ApiException.class, () -> service.processPayment(pay(S1), "me", KEY));

        assertEquals(ErrorCode.HOLD_EXPIRED, e.getErrorCode());
        verify(paymentMapper, never()).insertPayment(any(PaymentDTO.class));
        verify(idempotency).abort("me", KEY);
    }

    @Test
    @DisplayName("결제 성공: 좌석마다 seat + reservation(CONFIRMED) INSERT, 결제 1건, 커밋 후 이벤트, 결과 저장")
    void confirmsSeats() {
        when(seatHoldService.getMyHolds(S1, "me")).thenReturn(seats("1A", "1B"));

        PaymentResponseDTO res = service.processPayment(pay(S1), "me", KEY);

        assertEquals("APPROVED", res.getStatus());
        assertEquals(PRICE * 2, res.getAmount());
        verify(seatHoldService).verifyAndExtend(eq(S1), eq("me"), any(), anyLong());
        verify(seatMapper, times(2)).insertOccupiedSeat(any());
        verify(reservationMapper, times(2)).insertReservation(any());
        verify(paymentMapper, times(1)).insertPayment(any(PaymentDTO.class));
        verify(events, times(1)).publishEvent(any(PaymentConfirmedEvent.class));
        verify(idempotency).complete("me", KEY, res);
    }

    @Test
    @DisplayName("왕복: 가는 편·오는 편을 한 트랜잭션, 결제 1건으로 확정하고 운행별 이벤트 2개")
    void confirmsRoundTrip() {
        when(seatHoldService.getMyHolds(S1, "me")).thenReturn(seats("1A", "1B"));
        when(seatHoldService.getMyHolds(S2, "me")).thenReturn(seats("3C"));

        PaymentResponseDTO res = service.processPayment(pay(S1, S2), "me", KEY);

        assertEquals(PRICE * 3, res.getAmount());
        verify(seatMapper, times(3)).insertOccupiedSeat(any());
        verify(paymentMapper, times(1)).insertPayment(any(PaymentDTO.class));
        verify(events, times(2)).publishEvent(any(PaymentConfirmedEvent.class));
    }

    @Test
    @DisplayName("같은 멱등키 재요청: 저장된 결과를 그대로 돌려주고 결제는 다시 하지 않는다")
    void replaysSameKey() {
        PaymentResponseDTO saved = PaymentResponseDTO.builder().status("APPROVED").ticketNumber("1111-2222-3333-44").build();
        when(idempotency.begin("me", KEY)).thenReturn(saved);

        PaymentResponseDTO res = service.processPayment(pay(S1), "me", KEY);

        assertSame(saved, res);
        verify(seatHoldService, never()).getMyHolds(anyLong(), any());
        verify(paymentMapper, never()).insertPayment(any(PaymentDTO.class));
    }

    @Test
    @DisplayName("같은 키가 아직 처리 중이면 409 (더블클릭)")
    void rejectsInProgress() {
        when(idempotency.begin("me", KEY)).thenThrow(new ApiException(ErrorCode.PAYMENT_IN_PROGRESS));

        ApiException e = assertThrows(ApiException.class, () -> service.processPayment(pay(S1), "me", KEY));

        assertEquals(ErrorCode.PAYMENT_IN_PROGRESS, e.getErrorCode());
        verify(paymentMapper, never()).insertPayment(any(PaymentDTO.class));
    }

    @Test
    @DisplayName("모의 PG 거절: 402, 선점 즉시 반납 + 대기열 슬롯 반납, DB 에는 아무것도 쓰지 않음")
    void declinedReleasesHolds() {
        List<SeatSelection> mine = seats("1A");
        when(seatHoldService.getMyHolds(S1, "me")).thenReturn(mine);
        when(seatHoldService.release(S1, "me", mine)).thenReturn(mine);
        PaymentRequestDTO req = pay(S1);
        req.setMockResult("FAIL");

        ApiException e = assertThrows(ApiException.class, () -> service.processPayment(req, "me", KEY));

        assertEquals(ErrorCode.PAYMENT_DECLINED, e.getErrorCode());
        verify(seatHoldService).release(S1, "me", mine);
        verify(queuePassService).releaseSlot(S1, "me");
        verify(seatMapper, never()).insertOccupiedSeat(any());
        verify(idempotency).abort("me", KEY);
    }

    @Test
    @DisplayName("seat INSERT 가 UNIQUE 충돌로 0건이면(최후 방어선) 409, 결제 INSERT·커밋 후 이벤트 없음")
    void uniqueConflictReturns409WithoutPayment() {
        when(seatHoldService.getMyHolds(S1, "me")).thenReturn(seats("1A"));
        doReturn(0).when(seatMapper).insertOccupiedSeat(any()); // 이미 다른 사람이 확정한 좌석

        ApiException e = assertThrows(ApiException.class, () -> service.processPayment(pay(S1), "me", KEY));

        assertEquals(ErrorCode.SEAT_ALREADY_TAKEN, e.getErrorCode());
        verify(paymentMapper, never()).insertPayment(any(PaymentDTO.class));
        verify(events, never()).publishEvent(any());
    }

    @Test
    @DisplayName("요청 금액이 서버 계산과 다르면 거절 (위변조 방지)")
    void rejectsTamperedAmount() {
        when(seatHoldService.getMyHolds(S1, "me")).thenReturn(seats("1A"));
        PaymentRequestDTO req = pay(S1);
        req.setTotalPrice(100);

        ApiException e = assertThrows(ApiException.class, () -> service.processPayment(req, "me", KEY));

        assertEquals(ErrorCode.PAYMENT_AMOUNT_INVALID, e.getErrorCode());
    }
}
