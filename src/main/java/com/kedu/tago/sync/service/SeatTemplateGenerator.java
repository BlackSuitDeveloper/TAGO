package com.kedu.tago.sync.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** KTX 1~18호차 고정 좌석 템플릿 (전 좌석 일반석, 총 935석) */
@Component
public class SeatTemplateGenerator {

    public static final String GRADE_STANDARD = "STANDARD";
    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final int TOTAL_KTX_SEATS = 935;

    private static final char[] SEAT_COLUMNS = {'A', 'B', 'C', 'D'};

    // 호차별 좌석 수 (합계 935석)
    private static final int[] CAR_SEAT_COUNTS = {
            56, // 1호차
            35, // 2호차
            46, // 3호차
            50, // 4호차
            55, // 5호차
            56, // 6호차
            56, // 7호차
            56, // 8호차
            56, // 9호차
            56, // 10호차
            56, // 11호차
            56, // 12호차
            56, // 13호차
            56, // 14호차
            56, // 15호차
            56, // 16호차
            56, // 17호차
            21  // 18호차
    };

    @Getter
    @AllArgsConstructor
    public static class SeatTemplateItem {
        private final int carNo;
        private final String seatNo;
        private final String seatGrade;
    }

    private final List<SeatTemplateItem> ktxSeatTemplate;

    public SeatTemplateGenerator() {
        List<SeatTemplateItem> items = new ArrayList<>(TOTAL_KTX_SEATS);
        for (int carIdx = 0; carIdx < CAR_SEAT_COUNTS.length; carIdx++) {
            int carNo = carIdx + 1;
            int seatCount = CAR_SEAT_COUNTS[carIdx];

            for (int s = 0; s < seatCount; s++) {
                int row = (s / SEAT_COLUMNS.length) + 1;
                char col = SEAT_COLUMNS[s % SEAT_COLUMNS.length];
                String seatNo = row + String.valueOf(col);
                items.add(new SeatTemplateItem(carNo, seatNo, GRADE_STANDARD));
            }
        }
        this.ktxSeatTemplate = Collections.unmodifiableList(items);
    }

    /** 호차별 좌석 수 조회 */
    public int[] getCarSeatCounts() {
        return CAR_SEAT_COUNTS;
    }

    /** 호차의 좌석 목록 조회 */
    public List<SeatTemplateItem> getSeatsByCarNo(int carNo) {
        if (carNo < 1 || carNo > CAR_SEAT_COUNTS.length) {
            return Collections.emptyList();
        }
        List<SeatTemplateItem> result = new ArrayList<>(CAR_SEAT_COUNTS[carNo - 1]);
        for (SeatTemplateItem item : ktxSeatTemplate) {
            if (item.getCarNo() == carNo) {
                result.add(item);
            }
        }
        return result;
    }

    /** 호차와 좌석 번호로 템플릿 좌석 조회 (없으면 null) */
    public SeatTemplateItem findTemplateSeat(int carNo, String seatNo) {
        if (seatNo == null) return null;
        for (SeatTemplateItem item : ktxSeatTemplate) {
            if (item.getCarNo() == carNo && item.getSeatNo().equals(seatNo)) {
                return item;
            }
        }
        return null;
    }
}
