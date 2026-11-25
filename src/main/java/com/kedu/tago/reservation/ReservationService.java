package com.kedu.tago.reservation;

import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.queue.QueueService;
import com.kedu.tago.reservation.dto.MyTicketResponseDTO;
import com.kedu.tago.reservation.dto.ReservationDTO;
import com.kedu.tago.reservation.dto.ReservationRequestDTO;
import com.kedu.tago.reservation.dto.ReservationResponseDTO;
import com.kedu.tago.schedule.ScheduleService;
import com.kedu.tago.schedule.dto.ScheduleResponseDTO;
import com.kedu.tago.seat.SeatHoldService;
import com.kedu.tago.seat.SeatMapper;
import com.kedu.tago.seat.SeatService;
import com.kedu.tago.seat.SeatTemplateGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 예매 서비스 (Redis 좌석 선점·해제, 예매 취소) */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ReservationMapper reservationMapper;
    private final SeatMapper seatMapper;
    private final SeatService seatService;
    private final ScheduleService scheduleService;
    private final SeatTemplateGenerator seatTemplateGenerator;
    private final QueueService queueService;
    private final SeatHoldService seatHoldService;

    public static final int MAX_SEATS_PER_HOLD = 6; // 1회 최대 선점 가능 좌석 수

    /** 좌석 선점 (Redis held 키, DB 에는 쓰지 않음. 확정은 결제 시점) */
    public ReservationResponseDTO holdSeats(ReservationRequestDTO request, String memberId, String queueToken) {
        Long scheduleSeq = request.getScheduleSeq();
        List<ReservationRequestDTO.SeatSelection> normalizedSeats = normalizeSelections(request);

        if (scheduleSeq == null || normalizedSeats.isEmpty()) {
            throw new ApiException(ErrorCode.SEAT_NOT_SELECTED);
        }
        if (memberId == null) {
            throw new ApiException(ErrorCode.LOGIN_REQUIRED);
        }

        // 대기열 입장권 검증 및 선점 유효시간만큼 active 연장
        queueService.validateAndExtendForHold(queueToken, scheduleSeq, memberId, seatHoldService.getHoldTtlSeconds());

        if (normalizedSeats.size() > MAX_SEATS_PER_HOLD) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "1회 최대 " + MAX_SEATS_PER_HOLD + "석까지 예매할 수 있습니다.");
        }

        ScheduleResponseDTO schedule = validateBookableSchedule(scheduleSeq);
        int unitPrice = unitPrice(schedule);

        // 좌석 템플릿 검증 (없는 호차/좌석 차단)
        for (ReservationRequestDTO.SeatSelection selection : normalizedSeats) {
            if (seatTemplateGenerator.findTemplateSeat(selection.getCarNo(), selection.getSeatNo()) == null) {
                throw new ApiException(ErrorCode.SEAT_NOT_FOUND,
                        "호차 " + selection.getCarNo() + ", 좌석 " + selection.getSeatNo() + " 정보를 찾을 수 없습니다.");
            }
        }

        // Redisson 락 안에서 선점 확인 + held 키 저장
        LocalDateTime expiresAt = seatHoldService.hold(scheduleSeq, memberId, normalizedSeats);

        List<String> heldSeatNos = new ArrayList<>();
        for (ReservationRequestDTO.SeatSelection selection : normalizedSeats) {
            seatService.broadcastSeatStatusChange(scheduleSeq, selection.getCarNo(), selection.getSeatNo(), "HELD");
            heldSeatNos.add(selection.getCarNo() + "-" + selection.getSeatNo());
        }

        return ReservationResponseDTO.builder()
                .scheduleSeq(scheduleSeq)
                .carNo(normalizedSeats.get(0).getCarNo())
                .seatList(heldSeatNos)
                .totalPrice(unitPrice * normalizedSeats.size())
                .status("HELD")
                .reservedAt(LocalDateTime.now())
                .expiresAt(expiresAt)
                .build();
    }

    /** 좌석 다시 고르기: 내 선점만 해제 (active 유지) */
    public int releaseHolds(Long scheduleSeq, String memberId) {
        if (scheduleSeq == null || memberId == null) {
            return 0;
        }
        List<ReservationRequestDTO.SeatSelection> released =
                seatHoldService.release(scheduleSeq, memberId, seatHoldService.getMyHolds(scheduleSeq, memberId));
        for (ReservationRequestDTO.SeatSelection s : released) {
            seatService.broadcastSeatStatusChange(scheduleSeq, s.getCarNo(), s.getSeatNo(), "AVAILABLE");
        }
        return released.size();
    }

    /** 예매 그만두기 / 페이지 이탈: 선점 해제 + 대기열 슬롯 반납 */
    public void leave(Long scheduleSeq, String memberId) {
        releaseHolds(scheduleSeq, memberId);
        queueService.releaseSlot(scheduleSeq, memberId);
    }

    /** 운행 일정 상태·오픈 시각·출발 시각 검증 */
    public ScheduleResponseDTO validateBookableSchedule(Long scheduleSeq) {
        ScheduleResponseDTO schedule = scheduleService.getScheduleDetail(scheduleSeq);
        if (schedule == null) {
            throw new ApiException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now(KST);
        if (schedule.getStatus() != null && !"SCHEDULED".equals(schedule.getStatus())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "예매 가능한 운행 상태가 아닙니다.");
        }
        if (schedule.getBookingOpenAt() != null && now.isBefore(schedule.getBookingOpenAt())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "아직 예매 오픈 시간이 되지 않았습니다.");
        }
        if (schedule.getDepPlandTime() != null && !schedule.getDepPlandTime().isAfter(now)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "이미 출발한 열차는 예매할 수 없습니다.");
        }
        return schedule;
    }

    /** 1석 단가 */
    public static int unitPrice(ScheduleResponseDTO schedule) {
        return (schedule.getStandardPrice() != null && schedule.getStandardPrice() > 0)
                ? schedule.getStandardPrice().intValue() : 59800;
    }

    /** 좌석 선택 목록 정규화 (좌석별 호차 지원, 중복 제거, 데드락 방지용 고정 정렬) */
    private List<ReservationRequestDTO.SeatSelection> normalizeSelections(ReservationRequestDTO request) {
        Map<String, ReservationRequestDTO.SeatSelection> unique = new LinkedHashMap<>();

        if (request.getSeats() != null && !request.getSeats().isEmpty()) {
            for (ReservationRequestDTO.SeatSelection s : request.getSeats()) {
                if (s != null && s.getCarNo() != null && s.getSeatNo() != null && !s.getSeatNo().isBlank()) {
                    String cleanSeat = s.getSeatNo().trim();
                    unique.putIfAbsent(s.getCarNo() + ":" + cleanSeat,
                            new ReservationRequestDTO.SeatSelection(s.getCarNo(), cleanSeat));
                }
            }
        } else if (request.getCarNo() != null && request.getSeatList() != null) {
            for (String seatNo : request.getSeatList()) {
                if (seatNo != null && !seatNo.isBlank()) {
                    String cleanSeat = seatNo.trim();
                    unique.putIfAbsent(request.getCarNo() + ":" + cleanSeat,
                            new ReservationRequestDTO.SeatSelection(request.getCarNo(), cleanSeat));
                }
            }
        }

        List<ReservationRequestDTO.SeatSelection> sorted = new ArrayList<>(unique.values());
        sorted.sort(Comparator.comparing(ReservationRequestDTO.SeatSelection::getCarNo)
                .thenComparing(ReservationRequestDTO.SeatSelection::getSeatNo));
        return sorted;
    }

    /** 예매 취소 및 좌석 반환 */
    @Transactional
    public void cancelReservation(Long reservationSeq, String memberId) {
        ReservationDTO reservation = reservationMapper.selectReservationById(reservationSeq);
        if (reservation == null) {
            throw new ApiException(ErrorCode.RESERVATION_NOT_FOUND);
        }

        if (memberId == null || !memberId.equals(reservation.getMemberId())) {
            throw new ApiException(ErrorCode.NOT_RESOURCE_OWNER, "본인의 예매만 취소할 수 있습니다.");
        }

        if ("CANCELLED".equals(reservation.getStatus())) {
            throw new ApiException(ErrorCode.RESERVATION_ALREADY_CANCELLED);
        }

        // 1. 예매 상태를 취소로 조건부 전이 (이미 취소되지 않은 경우에만)
        int updated = reservationMapper.updateReservationStatus(reservationSeq, "CANCELLED", "USER_CANCEL");
        if (updated == 0) {
            throw new ApiException(ErrorCode.RESERVATION_ALREADY_CANCELLED);
        }

        // 2. 점유 좌석 삭제 (AVAILABLE 복구)
        if (reservation.getSeatSeq() != null) {
            seatMapper.deleteSeatBySeq(reservation.getSeatSeq());
        }

        // 3. 대기열 슬롯 반납
        queueService.releaseSlot(reservation.getScheduleSeq(), memberId);

        // 4. 트랜잭션 커밋 후 실시간 SSE 복구 알림
        if (reservation.getScheduleSeq() != null && reservation.getCarNo() != null && reservation.getSeatNo() != null) {
            broadcastAfterCommit(reservation.getScheduleSeq(), reservation.getCarNo(), reservation.getSeatNo(), "AVAILABLE");
        }
    }

    /** 내 예매 목록 조회 */
    public List<MyTicketResponseDTO> getMyReservations(String memberId) {
        if (memberId == null) {
            return List.of();
        }
        return reservationMapper.selectMyReservations(memberId);
    }

    /** 트랜잭션 커밋이 확정된 뒤에만 SSE 브로드캐스트를 수행 (롤백 시 유령 알림 방지) */
    private void broadcastAfterCommit(Long scheduleSeq, Integer carNo, String seatNo, String status) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    seatService.broadcastSeatStatusChange(scheduleSeq, carNo, seatNo, status);
                }
            });
        } else {
            seatService.broadcastSeatStatusChange(scheduleSeq, carNo, seatNo, status);
        }
    }
}
