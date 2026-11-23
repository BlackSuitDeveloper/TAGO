package com.kedu.tago.sync.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 공공데이터(TAGO) 조회 대상 KTX 노선 쌍, KTX 1세대 1~46호기 마스터 배정, 미제공 필드 보정 규칙 설정.
 */
@Component
public class TagoRouteConfig {

    public static final int TOTAL_KTX_UNITS = 46;

    @Getter
    @AllArgsConstructor
    public static class RoutePair {
        private final String depStationName; // 출발역명 (station.nodename 매핑)
        private final String arrStationName; // 도착역명 (station.nodename 매핑)
        private final String lineCategory;   // 노선 분류 (train.line_category)
        private final int fallbackFare;      // TAGO adultcharge=0 응답 시 보정용 기본 일반석 요금
    }

    /**
     * KTX 1세대 1호기 ~ 46호기(trainno 1 ~ 46)의 노선별 고정 배정 맵
     * - 1 ~ 25호기 (25대): 경부선
     * - 26 ~ 33호기 (8대): 호남선
     * - 34 ~ 39호기 (6대): 전라선
     * - 40 ~ 43호기 (4대): 경전선
     * - 44 ~ 46호기 (3대): 동해선
     */
    private static final Map<Integer, String> KTX_UNIT_LINE_MAP;

    static {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (int unit = 1; unit <= 25; unit++) map.put(unit, "경부선");
        for (int unit = 26; unit <= 33; unit++) map.put(unit, "호남선");
        for (int unit = 34; unit <= 39; unit++) map.put(unit, "전라선");
        for (int unit = 40; unit <= 43; unit++) map.put(unit, "경전선");
        for (int unit = 44; unit <= 46; unit++) map.put(unit, "동해선");
        KTX_UNIT_LINE_MAP = Collections.unmodifiableMap(map);
    }

    public Map<Integer, String> getFixedKtxUnitLineMap() {
        return KTX_UNIT_LINE_MAP;
    }

    /**
     * TAGO API의 원본 열차번호(rawTrainNo)를 해당 노선(lineCategory)에 배정된 KTX 1~46호기 번호로 매핑
     */
    public int mapToKtxUnitNo(int rawTrainNo, String lineCategory) {
        int positiveNo = Math.max(1, rawTrainNo);
        if (lineCategory == null) {
            return 1 + Math.floorMod(positiveNo - 1, 25);
        }
        return switch (lineCategory) {
            case "호남선" -> 26 + Math.floorMod(positiveNo - 1, 8);  // 26 ~ 33호기
            case "전라선" -> 34 + Math.floorMod(positiveNo - 1, 6);  // 34 ~ 39호기
            case "경전선" -> 40 + Math.floorMod(positiveNo - 1, 4);  // 40 ~ 43호기
            case "동해선" -> 44 + Math.floorMod(positiveNo - 1, 3);  // 44 ~ 46호기
            default -> 1 + Math.floorMod(positiveNo - 1, 25);        // 경부선 등: 1 ~ 25호기
        };
    }

    /**
     * 조회 대상 KTX 1세대 노선당 대표 시종착 구간 (상·하행 총 10개 구간 상수 관리)
     * 중간 경유역 중복 적재를 방지하기 위해 각 노선의 시발역 ↔ 종착역만 1:1로 정의합니다.
     */
    private static final List<RoutePair> TARGET_ROUTES = List.of(
            // 1. 경부선 시종착 (서울 ↔ 부산)
            new RoutePair("서울", "부산", "경부선", 59800),
            new RoutePair("부산", "서울", "경부선", 59800),

            // 2. 호남선 시종착 (용산 ↔ 목포)
            new RoutePair("용산", "목포", "호남선", 52800),
            new RoutePair("목포", "용산", "호남선", 52800),

            // 3. 전라선 시종착 (용산 ↔ 여수EXPO)
            new RoutePair("용산", "여수EXPO", "전라선", 47500),
            new RoutePair("여수EXPO", "용산", "전라선", 47500),

            // 4. 경전선 시종착 (서울 ↔ 진주)
            new RoutePair("서울", "진주", "경전선", 57600),
            new RoutePair("진주", "서울", "경전선", 57600),

            // 5. 동해선 시종착 (서울 ↔ 포항)
            new RoutePair("서울", "포항", "동해선", 53600),
            new RoutePair("포항", "서울", "동해선", 53600)
    );

    /**
     * 주요 기차역 상세 주소 매핑 (TAGO API에서 제공하지 않는 station.node_address 보정용)
     */
    private static final Map<String, String> STATION_ADDRESS_MAP = Map.ofEntries(
            Map.entry("서울", "서울특별시 용산구 한강대로 405"),
            Map.entry("용산", "서울특별시 용산구 한강대로23길 55"),
            Map.entry("영등포", "서울특별시 영등포구 경인로 846"),
            Map.entry("청량리", "서울특별시 동대문구 왕산로 214"),
            Map.entry("상봉", "서울특별시 중랑구 망우로 297"),
            Map.entry("수서", "서울특별시 강남구 밤고개로 99"),
            Map.entry("행신", "경기도 고양시 덕양구 소원로 102"),
            Map.entry("광명", "경기도 광명시 광명역로 21"),
            Map.entry("수원", "경기도 수원시 팔달구 덕영대로 924"),
            Map.entry("천안아산", "충청남도 아산시 배방읍 희망로 100"),
            Map.entry("오송", "충청북도 청주시 흥덕구 오송읍 오송가락로 123"),
            Map.entry("대전", "대전광역시 동구 중앙로 215"),
            Map.entry("서대전", "대전광역시 중구 오류로 23"),
            Map.entry("김천구미", "경상북도 김천시 남면 혁신1로 51"),
            Map.entry("동대구", "대구광역시 동구 동대구로 550"),
            Map.entry("서대구", "대구광역시 서구 와룡로 527"),
            Map.entry("경주", "경상북도 경주시 건천읍 경주역로 80"),
            Map.entry("신경주", "경상북도 경주시 건천읍 경주역로 80"),
            Map.entry("울산", "울산광역시 울주군 삼남읍 울산역로 177"),
            Map.entry("부산", "부산광역시 동구 중앙대로 206"),
            Map.entry("포항", "경상북도 포항시 북구 흥해읍 포항역로 1"),
            Map.entry("밀양", "경상남도 밀양시 중앙로 62"),
            Map.entry("창원중앙", "경상남도 창원시 의창구 상남로 383"),
            Map.entry("창원", "경상남도 창원시 의창구 의창대로 67"),
            Map.entry("마산", "경상남도 창원시 마산회원구 마산역광장로 18"),
            Map.entry("진주", "경상남도 진주시 진주역로 130"),
            Map.entry("공주", "충청남도 공주시 이인면 새빛로 100"),
            Map.entry("익산", "전북특별자치도 익산시 익산대로 153"),
            Map.entry("정읍", "전북특별자치도 정읍시 서부산업도로 305"),
            Map.entry("광주송정", "광주광역시 광산구 상무대로 201"),
            Map.entry("나주", "전라남도 나주시 나주역길 56"),
            Map.entry("목포", "전라남도 목포시 영산로 98"),
            Map.entry("전주", "전북특별자치도 전주시 덕진구 동부대로 680"),
            Map.entry("남원", "전북특별자치도 남원시 교룡로 71"),
            Map.entry("순천", "전라남도 순천시 팔마로 135"),
            Map.entry("여천", "전라남도 여수시 시청로 200"),
            Map.entry("여수EXPO", "전라남도 여수시 망양로 2"),
            Map.entry("여수엑스포", "전라남도 여수시 망양로 2"),
            Map.entry("만종", "강원특별자치도 원주시 호저면 운동들2길 23-41"),
            Map.entry("원주", "강원특별자치도 원주시 북원로 1860"),
            Map.entry("평창", "강원특별자치도 평창군 용평면 평창대로 1505"),
            Map.entry("진부", "강원특별자치도 평창군 진부면 송정길 100"),
            Map.entry("강릉", "강원특별자치도 강릉시 용지로 176"),
            Map.entry("정동진", "강원특별자치도 강릉시 강동면 정동역길 17"),
            Map.entry("묵호", "강원특별자치도 동해시 해안로 520"),
            Map.entry("동해", "강원특별자치도 동해시 송정로 71"),
            Map.entry("제천", "충청북도 제천시 의림대로 1"),
            Map.entry("영주", "경상북도 영주시 선비로 64"),
            Map.entry("안동", "경상북도 안동시 경동로 382-7"),
            Map.entry("부전", "부산광역시 부산진구 부전로 181")
    );


    public List<RoutePair> getTargetRoutes() {
        return TARGET_ROUTES;
    }

    /**
     * 역 이름과 시/도 이름으로 역 주소(station.node_address) 보정
     */
    public String resolveStationAddress(String nodename, String cityname) {
        if (nodename != null && STATION_ADDRESS_MAP.containsKey(nodename.trim())) {
            return STATION_ADDRESS_MAP.get(nodename.trim());
        }
        String cleanCity = (cityname != null && !cityname.isBlank()) ? cityname.trim() : "대한민국";
        String cleanNode = (nodename != null && !nodename.isBlank()) ? nodename.trim() : "기차";
        return cleanCity + " " + cleanNode + "역";
    }

}
