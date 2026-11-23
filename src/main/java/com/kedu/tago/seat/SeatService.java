package com.kedu.tago.seat;

import com.kedu.tago.seat.dto.CarSummaryDTO;
import com.kedu.tago.seat.dto.SeatDTO;
import com.kedu.tago.seat.dto.SeatResponseDTO;
import com.kedu.tago.sync.service.SeatTemplateGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatMapper seatMapper;
    private final SeatTemplateGenerator seatTemplateGenerator;

    // scheduleSeq -> SseEmitter 리스트 관리
    private final Map<Long, List<SseEmitter>> sseEmitters = new ConcurrentHashMap<>();

    /**
     * 특정 일정의 좌석 목록 및 호차 요약 조회 (고정 템플릿 + DB 점유 좌석 병합)
     *
     * @param scheduleSeq 운행 일정 시퀀스 (필수)
     * @param carNo       호차 번호 (선택, 미입력 시 1호차 기본 선택)
     * @return 통합 좌석 응답 DTO
     */
    public SeatResponseDTO getSeats(Long scheduleSeq, Integer carNo) {
        // 1. DB에 저장된 호차별 점유 좌석 수 조회
        List<CarSummaryDTO> occupiedSummaries = seatMapper.selectCarSummaries(scheduleSeq);
        Map<Integer, Integer> occupiedCountByCar = new HashMap<>();
        if (occupiedSummaries != null) {
            for (CarSummaryDTO item : occupiedSummaries) {
                if (item.getCarNo() != null) {
                    occupiedCountByCar.put(item.getCarNo(), item.getTotalSeats() != null ? item.getTotalSeats() : 0);
                }
            }
        }

        // 2. 1~18호차 전체 요약 목록 생성 (템플릿 좌석 수 - 점유 좌석 수)
        int[] carSeatCounts = seatTemplateGenerator.getCarSeatCounts();
        List<CarSummaryDTO> carSummaryList = new ArrayList<>(carSeatCounts.length);
        for (int idx = 0; idx < carSeatCounts.length; idx++) {
            int currentCarNo = idx + 1;
            int total = carSeatCounts[idx];
            int occupied = occupiedCountByCar.getOrDefault(currentCarNo, 0);
            int available = Math.max(0, total - occupied);

            carSummaryList.add(CarSummaryDTO.builder()
                    .carNo(currentCarNo)
                    .totalSeats(total)
                    .availableSeats(available)
                    .build());
        }

        // 3. 조회 대상 호차 결정 (미지정 시 기본 1호차)
        int targetCarNo = (carNo != null && carNo >= 1 && carNo <= carSeatCounts.length) ? carNo : 1;

        // 4. DB에서 해당 호차의 점유된 좌석만 조회 후 Map(seatNo -> SeatDTO)으로 매핑
        List<SeatDTO> occupiedSeats = seatMapper.selectSeatList(scheduleSeq, targetCarNo);
        Map<String, SeatDTO> occupiedSeatMap = new HashMap<>();
        if (occupiedSeats != null) {
            for (SeatDTO occupied : occupiedSeats) {
                occupiedSeatMap.put(occupied.getSeatNo(), occupied);
            }
        }

        // 5. 템플릿 좌석 위에 점유 상태 덮어쓰기 (미점유 좌석은 AVAILABLE)
        List<SeatTemplateGenerator.SeatTemplateItem> templateSeats = seatTemplateGenerator.getSeatsByCarNo(targetCarNo);
        List<SeatDTO> seatList = new ArrayList<>(templateSeats.size());
        for (SeatTemplateGenerator.SeatTemplateItem tpl : templateSeats) {
            SeatDTO occupied = occupiedSeatMap.get(tpl.getSeatNo());
            if (occupied != null) {
                seatList.add(occupied);
            } else {
                seatList.add(SeatDTO.builder()
                        .scheduleSeq(scheduleSeq)
                        .carNo(targetCarNo)
                        .seatNo(tpl.getSeatNo())
                        .seatGrade(tpl.getSeatGrade())
                        .status(SeatTemplateGenerator.STATUS_AVAILABLE)
                        .version(0)
                        .build());
            }
        }

        CarSummaryDTO currentSummary = carSummaryList.get(targetCarNo - 1);

        return SeatResponseDTO.builder()
                .scheduleSeq(scheduleSeq)
                .carNo(targetCarNo)
                .totalSeats(currentSummary.getTotalSeats())
                .availableSeats(currentSummary.getAvailableSeats())
                .carSummaryList(carSummaryList)
                .seatList(seatList)
                .build();
    }

    /**
     * 실시간 좌석 상태 SSE 연결 생성
     */
    public SseEmitter subscribeSeatStream(Long scheduleSeq) {
        SseEmitter emitter = new SseEmitter(60 * 1000L * 30); // 30분 타임아웃
        List<SseEmitter> list = sseEmitters.computeIfAbsent(scheduleSeq, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        emitter.onCompletion(() -> list.remove(emitter));
        emitter.onTimeout(() -> list.remove(emitter));
        emitter.onError(e -> list.remove(emitter));

        try {
            // 초기 연결 확인 이벤트 발송
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data(Map.of("message", "SSE connected for scheduleSeq " + scheduleSeq)));
        } catch (IOException e) {
            list.remove(emitter);
        }

        return emitter;
    }

    /**
     * 좌석 상태 변경 시 해당 일정 구독자들에게 SSE 브로드캐스트
     */
    public void broadcastSeatStatusChange(Long scheduleSeq, Integer carNo, String seatNo, String newStatus) {
        List<SseEmitter> list = sseEmitters.get(scheduleSeq);
        if (list == null || list.isEmpty()) return;

        Map<String, Object> payload = Map.of(
                "scheduleSeq", scheduleSeq,
                "carNo", carNo,
                "seatNo", seatNo,
                "status", newStatus
        );

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("SEAT_UPDATE").data(payload));
            } catch (IOException e) {
                list.remove(emitter);
            }
        }
    }
}
