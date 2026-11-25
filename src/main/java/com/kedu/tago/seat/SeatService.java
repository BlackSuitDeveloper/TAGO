package com.kedu.tago.seat;

import com.kedu.tago.seat.dto.CarSummaryDTO;
import com.kedu.tago.seat.hold.SeatHoldService;
import com.kedu.tago.seat.dto.SeatDTO;
import com.kedu.tago.seat.dto.SeatResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/** 좌석 조회 (고정 템플릿 + DB 확정 좌석 + Redis 선점) */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatMapper seatMapper;
    private final SeatTemplateGenerator seatTemplateGenerator;
    private final SeatHoldService seatHoldService;

    /** 일정의 좌석 목록 및 호차 요약 조회 (고정 템플릿 + DB 점유 좌석 병합) */
    public SeatResponseDTO getSeats(Long scheduleSeq, Integer carNo) {
        // 호차별 점유 좌석 수 조회
        List<CarSummaryDTO> occupiedSummaries = seatMapper.selectCarSummaries(scheduleSeq);
        Map<Integer, Integer> occupiedCountByCar = new HashMap<>();
        if (occupiedSummaries != null) {
            for (CarSummaryDTO item : occupiedSummaries) {
                if (item.getCarNo() != null) {
                    occupiedCountByCar.put(item.getCarNo(), item.getTotalSeats() != null ? item.getTotalSeats() : 0);
                }
            }
        }

        // 1~18호차 요약 생성 (템플릿 좌석 수 - 점유 좌석 수)
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

        // 조회 대상 호차 (미지정 시 1호차)
        int targetCarNo = (carNo != null && carNo >= 1 && carNo <= carSeatCounts.length) ? carNo : 1;

        // 해당 호차의 점유 좌석을 seatNo 기준 Map 으로 변환
        List<SeatDTO> occupiedSeats = seatMapper.selectSeatList(scheduleSeq, targetCarNo);
        Map<String, SeatDTO> occupiedSeatMap = new HashMap<>();
        if (occupiedSeats != null) {
            for (SeatDTO occupied : occupiedSeats) {
                occupiedSeatMap.put(occupied.getSeatNo(), occupied);
            }
        }

        // 템플릿 좌석 위에 점유 상태 덮어쓰기 (미점유는 AVAILABLE)
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

        // Redis 선점(HELD) 덮어쓰기: DB 에 없는(미확정) 좌석만 MGET 한 번으로 조회
        List<String> freeSeatNos = new ArrayList<>();
        for (SeatDTO seat : seatList) {
            if (SeatTemplateGenerator.STATUS_AVAILABLE.equals(seat.getStatus())) {
                freeSeatNos.add(seat.getSeatNo());
            }
        }
        Map<String, String> holders = seatHoldService.getHolders(scheduleSeq, targetCarNo, freeSeatNos);
        if (!holders.isEmpty()) {
            for (SeatDTO seat : seatList) {
                if (holders.containsKey(seat.getSeatNo())) {
                    seat.setStatus("HELD");
                }
            }
            CarSummaryDTO target = carSummaryList.get(targetCarNo - 1);
            target.setAvailableSeats(Math.max(0, target.getAvailableSeats() - holders.size()));
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
}
