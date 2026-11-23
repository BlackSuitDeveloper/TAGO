package com.kedu.tago.reservation;

import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.reservation.dto.MyTicketResponseDTO;
import com.kedu.tago.reservation.dto.ReservationDTO;
import com.kedu.tago.reservation.dto.ReservationRequestDTO;
import com.kedu.tago.reservation.dto.ReservationResponseDTO;
import com.kedu.tago.schedule.ScheduleService;
import com.kedu.tago.schedule.dto.ScheduleResponseDTO;
import com.kedu.tago.seat.SeatMapper;
import com.kedu.tago.seat.SeatService;
import com.kedu.tago.seat.dto.SeatDTO;
import com.kedu.tago.sync.service.SeatTemplateGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationMapper reservationMapper;
    private final SeatMapper seatMapper;
    private final SeatService seatService;
    private final ScheduleService scheduleService;
    private final SeatTemplateGenerator seatTemplateGenerator;

    public static final int HOLD_TTL_MINUTES = 5; // 좌석 선점 유효시간 5분

    /**
     * 좌석 선점 및 예매 생성 (R002: 조건부 UPDATE 기반 단일 성공 보장)
     */
    @Transactional
    public ReservationResponseDTO holdSeats(ReservationRequestDTO request, String memberId) {
        Long scheduleSeq = request.getScheduleSeq();
        Integer carNo = request.getCarNo();
        List<String> seatList = request.getSeatList();

        if (scheduleSeq == null || carNo == null || seatList == null || seatList.isEmpty()) {
            throw new ApiException(ErrorCode.SEAT_NOT_SELECTED);
        }

        // 운행 일정 확인 및 요금 계산
        ScheduleResponseDTO schedule = scheduleService.getScheduleDetail(scheduleSeq);
        if (schedule == null) {
            throw new ApiException(ErrorCode.SCHEDULE_NOT_FOUND);
        }

        int unitPrice = (schedule.getStandardPrice() != null && schedule.getStandardPrice() > 0)
                ? schedule.getStandardPrice().intValue() : 59800;
        int totalPrice = unitPrice * seatList.size();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(HOLD_TTL_MINUTES);

        List<Long> createdReservationSeqs = new ArrayList<>();

        for (String seatNo : seatList) {
            // 1. 고정 좌석 템플릿 검증 (존재하지 않는 호차/좌석 번호 차단)
            SeatTemplateGenerator.SeatTemplateItem templateSeat = seatTemplateGenerator.findTemplateSeat(carNo, seatNo);
            if (templateSeat == null) {
                throw new ApiException(ErrorCode.SEAT_NOT_FOUND, "호차 " + carNo + ", 좌석 " + seatNo + " 정보를 찾을 수 없습니다.");
            }

            // 2. 점유 좌석 INSERT 시도 (UNIQUE(schedule_seq, car_no, seat_no) + ON CONFLICT DO NOTHING 동시성 제어)
            SeatDTO seatToInsert = SeatDTO.builder()
                    .scheduleSeq(scheduleSeq)
                    .carNo(carNo)
                    .seatNo(seatNo)
                    .seatGrade(templateSeat.getSeatGrade())
                    .build();

            int inserted = seatMapper.insertOccupiedSeat(seatToInsert);
            if (inserted == 0 || seatToInsert.getSeatSeq() == null) {
                // 이미 다른 사용자가 선점하였거나 예약된 상태
                throw new ApiException(ErrorCode.SEAT_ALREADY_TAKEN, "좌석 " + seatNo + "은(는) 이미 선택되었거나 예약이 진행 중입니다.");
            }

            // 3. reservation 레코드 생성
            ReservationDTO reservation = ReservationDTO.builder()
                    .memberId(memberId != null ? memberId : "guest")
                    .scheduleSeq(scheduleSeq)
                    .seatSeq(seatToInsert.getSeatSeq())
                    .status("HELD")
                    .totalPrice(unitPrice)
                    .reservedAt(now)
                    .expiresAt(expiresAt)
                    .build();

            reservationMapper.insertReservation(reservation);
            createdReservationSeqs.add(reservation.getReservationSeq());

            // 4. 실시간 SSE 브로드캐스트
            seatService.broadcastSeatStatusChange(scheduleSeq, carNo, seatNo, "HELD");
        }

        Long mainReservationSeq = createdReservationSeqs.get(0);

        return ReservationResponseDTO.builder()
                .reservationId(mainReservationSeq)
                .reservationSeq(mainReservationSeq)
                .reservationIds(createdReservationSeqs)
                .scheduleSeq(scheduleSeq)
                .carNo(carNo)
                .seatList(seatList)
                .totalPrice(totalPrice)
                .status("HELD")
                .reservedAt(now)
                .expiresAt(expiresAt)
                .build();
    }

    /**
     * 예매 취소 및 좌석 반환 (MA002)
     */
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

        // 1. 예약 상태 취소로 변경 (chk_res_cancel_reason: USER_CANCEL, TTL_EXPIRED, PAYMENT_FAILED)
        reservationMapper.updateReservationStatus(reservationSeq, "CANCELLED", "USER_CANCEL");

        // 2. 점유 좌석 레코드 삭제 (즉시 AVAILABLE 상태로 복구)
        if (reservation.getSeatSeq() != null) {
            seatMapper.deleteSeatBySeq(reservation.getSeatSeq());
        }

        // 3. 실시간 SSE 알림
        // (scheduleSeq와 좌석 번호 조회 필요 시 seat 정보 조회 후 전송)
    }

    /**
     * 내 예매 목록 조회 (MA001)
     */
    public List<MyTicketResponseDTO> getMyReservations(String memberId) {
        if (memberId == null) {
            return List.of();
        }
        return reservationMapper.selectMyReservations(memberId);
    }

    /**
     * 좌석 선점 TTL 자동 회수 스케줄러 (R003: 5초마다 실행)
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void autoReclaimExpiredHeldSeats() {
        List<ReservationDTO> expiredList = reservationMapper.selectExpiredHeldReservations();
        if (expiredList == null || expiredList.isEmpty()) {
            return;
        }

        log.info("[TTL Scheduler] 만료된 선점 좌석 {}건 자동 회수 시작", expiredList.size());
        for (ReservationDTO r : expiredList) {
            // 1. 예약 상태 취소 처리
            reservationMapper.updateReservationStatus(r.getReservationSeq(), "CANCELLED", "TTL_EXPIRED");

            // 2. 점유 좌석 레코드 삭제 (AVAILABLE 복구)
            if (r.getSeatSeq() != null) {
                seatMapper.deleteSeatBySeq(r.getSeatSeq());
            }
            log.info("[TTL Scheduler] 예약 번호 {} (좌석 {}) 만료 해제 완료", r.getReservationSeq(), r.getSeatSeq());
        }
    }
}
