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
import com.kedu.tago.seat.SeatMapper;
import com.kedu.tago.seat.SeatService;
import com.kedu.tago.seat.SeatTemplateGenerator;
import com.kedu.tago.seat.dto.SeatDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 예매 서비스 (좌석 선점, 취소, 만료 회수) */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationMapper reservationMapper;
    private final SeatMapper seatMapper;
    private final SeatService seatService;
    private final ScheduleService scheduleService;
    private final SeatTemplateGenerator seatTemplateGenerator;
    private final QueueService queueService;

    public static final int HOLD_TTL_MINUTES = 5; // 좌석 선점 유효시간(분)
    public static final int MAX_SEATS_PER_HOLD = 6; // 1회 최대 선점 가능 좌석 수

    /** 좌석 선점 및 예매 생성 (대기열 입장권 검증 포함) */
    @Transactional
    public ReservationResponseDTO holdSeats(ReservationRequestDTO request, String memberId, String queueToken) {
        Long scheduleSeq = request.getScheduleSeq();
        List<ReservationRequestDTO.SeatSelection> normalizedSeats = normalizeSelections(request);

        if (scheduleSeq == null || normalizedSeats.isEmpty()) {
            throw new ApiException(ErrorCode.SEAT_NOT_SELECTED);
        }

        // 대기열 입장권 원자적 검증 및 선점 유효시간만큼 만료 연장
        queueService.validateAndExtendForHold(queueToken, scheduleSeq, memberId, HOLD_TTL_MINUTES * 60L);

        // 운행 일정 확인 및 요금 계산
        if (normalizedSeats.size() > MAX_SEATS_PER_HOLD) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "1회 최대 " + MAX_SEATS_PER_HOLD + "석까지 예매할 수 있습니다.");
        }

        // 운행 일정 상태·오픈 시각·출발 시각 검증
        ScheduleResponseDTO schedule = scheduleService.getScheduleDetail(scheduleSeq);
        if (schedule == null) {
            throw new ApiException(ErrorCode.SCHEDULE_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        if (schedule.getStatus() != null && !"SCHEDULED".equals(schedule.getStatus())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "예매 가능한 운행 상태가 아닙니다.");
        }
        if (schedule.getBookingOpenAt() != null && now.isBefore(schedule.getBookingOpenAt())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "아직 예매 오픈 시간이 되지 않았습니다.");
        }
        if (schedule.getDepPlandTime() != null && !schedule.getDepPlandTime().isAfter(now)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "이미 출발한 열차는 예매할 수 없습니다.");
        }

        int unitPrice = (schedule.getStandardPrice() != null && schedule.getStandardPrice() > 0)
                ? schedule.getStandardPrice().intValue() : 59800;
        int totalPrice = unitPrice * normalizedSeats.size();
        LocalDateTime expiresAt = now.plusMinutes(HOLD_TTL_MINUTES);

        List<Long> createdReservationSeqs = new ArrayList<>();
        List<String> reservedSeatNos = new ArrayList<>();

        for (ReservationRequestDTO.SeatSelection selection : normalizedSeats) {
            Integer carNo = selection.getCarNo();
            String seatNo = selection.getSeatNo();

            // 1. 좌석 템플릿 검증 (없는 호차/좌석 차단)
            SeatTemplateGenerator.SeatTemplateItem templateSeat = seatTemplateGenerator.findTemplateSeat(carNo, seatNo);
            if (templateSeat == null) {
                throw new ApiException(ErrorCode.SEAT_NOT_FOUND, "호차 " + carNo + ", 좌석 " + seatNo + " 정보를 찾을 수 없습니다.");
            }

            // 2. 점유 좌석 INSERT (UNIQUE + ON CONFLICT DO NOTHING 으로 동시성 제어)
            SeatDTO seatToInsert = SeatDTO.builder()
                    .scheduleSeq(scheduleSeq)
                    .carNo(carNo)
                    .seatNo(seatNo)
                    .seatGrade(templateSeat.getSeatGrade())
                    .build();

            int inserted;
            try {
                inserted = seatMapper.insertOccupiedSeat(seatToInsert);
            } catch (DeadlockLoserDataAccessException | CannotAcquireLockException e) {
                throw new ApiException(ErrorCode.SEAT_ALREADY_TAKEN, "좌석 " + carNo + "호차 " + seatNo + " 선점 경합이 발생했습니다. 다시 시도해 주세요.");
            }

            if (inserted == 0 || seatToInsert.getSeatSeq() == null) {
                // 이미 다른 사용자가 선점했거나 예약된 좌석
                throw new ApiException(ErrorCode.SEAT_ALREADY_TAKEN, "좌석 " + carNo + "호차 " + seatNo + "은(는) 이미 선택되었거나 예약이 진행 중입니다.");
            }

            // 3. 예매 레코드 생성 (좌석 삭제 후에도 이력을 유지할 수 있도록 car_no/seat_no/seat_grade 스냅샷 저장)
            ReservationDTO reservation = ReservationDTO.builder()
                    .memberId(memberId != null ? memberId : "guest")
                    .scheduleSeq(scheduleSeq)
                    .seatSeq(seatToInsert.getSeatSeq())
                    .carNo(carNo)
                    .seatNo(seatNo)
                    .seatGrade(templateSeat.getSeatGrade())
                    .status("HELD")
                    .totalPrice(unitPrice)
                    .reservedAt(now)
                    .expiresAt(expiresAt)
                    .build();

            reservationMapper.insertReservation(reservation);
            createdReservationSeqs.add(reservation.getReservationSeq());
            reservedSeatNos.add(seatNo);

            // 4. 좌석 상태 SSE 브로드캐스트
            seatService.broadcastSeatStatusChange(scheduleSeq, carNo, seatNo, "HELD");
        }

        Long mainReservationSeq = createdReservationSeqs.get(0);
        Integer primaryCarNo = normalizedSeats.get(0).getCarNo();

        return ReservationResponseDTO.builder()
                .reservationId(mainReservationSeq)
                .reservationSeq(mainReservationSeq)
                .reservationIds(createdReservationSeqs)
                .scheduleSeq(scheduleSeq)
                .carNo(primaryCarNo)
                .seatList(reservedSeatNos)
                .totalPrice(totalPrice)
                .status("HELD")
                .reservedAt(now)
                .expiresAt(expiresAt)
                .build();
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

        // TODO: 좌석 정보 조회 후 SSE 알림 전송
        // 3. 실시간 SSE 복구 알림
        if (reservation.getScheduleSeq() != null && reservation.getCarNo() != null && reservation.getSeatNo() != null) {
            seatService.broadcastSeatStatusChange(reservation.getScheduleSeq(), reservation.getCarNo(), reservation.getSeatNo(), "AVAILABLE");
        }
    }

    /** 내 예매 목록 조회 */
    public List<MyTicketResponseDTO> getMyReservations(String memberId) {
        if (memberId == null) {
            return List.of();
        }
        return reservationMapper.selectMyReservations(memberId);
    }

    /** 선점 시간이 만료된 좌석 자동 회수 (5초마다) */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void autoReclaimExpiredHeldSeats() {
        List<ReservationDTO> expiredList = reservationMapper.selectExpiredHeldReservations();
        if (expiredList == null || expiredList.isEmpty()) {
            return;
        }

        log.info("[TTL Scheduler] 만료된 선점 좌석 {}건 자동 회수 시작", expiredList.size());
        for (ReservationDTO r : expiredList) {
            // 1. HELD 이고 만료된 경우에만 조건부 취소 (결제 확정과 경합 시 안전)
            int updated = reservationMapper.cancelExpiredReservation(r.getReservationSeq());
            if (updated == 0) {
                continue;
            }

            // 2. 점유 좌석 삭제 (AVAILABLE 복구)
            if (r.getSeatSeq() != null) {
                seatMapper.deleteSeatBySeq(r.getSeatSeq());
            }

            // 3. 대기열 슬롯 반납
            queueService.releaseSlot(r.getScheduleSeq(), r.getMemberId());
            log.info("[TTL Scheduler] 예약 번호 {} (좌석 {}) 만료 해제 완료", r.getReservationSeq(), r.getSeatSeq());
            // 3. 실시간 SSE 복구 알림
            if (r.getScheduleSeq() != null && r.getCarNo() != null && r.getSeatNo() != null) {
                seatService.broadcastSeatStatusChange(r.getScheduleSeq(), r.getCarNo(), r.getSeatNo(), "AVAILABLE");
            }
            log.info("[TTL Scheduler] 예약 번호 {} ({}호차 {}) 만료 해제 완료",
                    r.getReservationSeq(), r.getCarNo(), r.getSeatNo());
        }
    }
}
