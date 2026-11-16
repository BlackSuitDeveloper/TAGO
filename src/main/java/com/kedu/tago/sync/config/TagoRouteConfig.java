package com.kedu.tago.sync.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 공공데이터(TAGO) 조회 대상 KTX 노선 쌍 및 미제공 필드(역 주소, 노선 구분, 기본 운임) 보정 규칙 설정.
 * 일일 API 호출 트래픽을 절약하기 위해 DB에 적재된 전체 역(station) 중 지정된 출발-도착 노선 쌍만 조회합니다.
 */
@Component
public class TagoRouteConfig {

    @Getter
    @AllArgsConstructor
    public static class RoutePair {
        private final String depStationName; // 출발역명 (station.nodename 매핑)
        private final String arrStationName; // 도착역명 (station.nodename 매핑)
        private final String lineCategory;   // 노선 분류 (train.line_category)
        private final int fallbackFare;      // TAGO adultcharge=0 응답 시 보정용 기본 일반석 요금
    }

    /**
     * 조회 대상 KTX 지정 출발-도착 노선 쌍 (상수 관리)
     * 필요 시 이 리스트에 노선 쌍만 추가하면 일간 배치 및 수동 동기화에 즉시 반영됩니다.
     */
    private static final List<RoutePair> TARGET_ROUTES = List.of(
            // 1. 경부선 주요 노선 (양방향)
            new RoutePair("서울", "부산", "경부선", 59800),
            new RoutePair("부산", "서울", "경부선", 59800),
            new RoutePair("서울", "동대구", "경부선", 43500),
            new RoutePair("동대구", "서울", "경부선", 43500),
            new RoutePair("서울", "대전", "경부선", 23700),
            new RoutePair("대전", "서울", "경부선", 23700),
            new RoutePair("대전", "부산", "경부선", 36200),
            new RoutePair("부산", "대전", "경부선", 36200),

            // 2. 호남선 주요 노선 (양방향)
            new RoutePair("용산", "광주송정", "호남선", 46800),
            new RoutePair("광주송정", "용산", "호남선", 46800),
            new RoutePair("용산", "목포", "호남선", 52800),
            new RoutePair("목포", "용산", "호남선", 52800),
            new RoutePair("용산", "익산", "호남선", 32000),
            new RoutePair("익산", "용산", "호남선", 32000),

            // 3. 전라선 주요 노선 (양방향)
            new RoutePair("용산", "여수엑스포", "전라선", 47500),
            new RoutePair("여수엑스포", "용산", "전라선", 47500),
            new RoutePair("용산", "전주", "전라선", 34400),
            new RoutePair("전주", "용산", "전라선", 34400),

            // 4. 강릉선 주요 노선 (양방향)
            new RoutePair("서울", "강릉", "강릉선", 27600),
            new RoutePair("강릉", "서울", "강릉선", 27600),

            // 5. 동해선 / 경전선 / 중앙선 주요 노선 (양방향)
            new RoutePair("서울", "포항", "동해선", 53600),
            new RoutePair("포항", "서울", "동해선", 53600),
            new RoutePair("서울", "진주", "경전선", 57600),
            new RoutePair("진주", "서울", "경전선", 57600),
            new RoutePair("청량리", "안동", "중앙선", 25100),
            new RoutePair("안동", "청량리", "중앙선", 25100)
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

    private static final Set<String> HONAM_STATIONS = Set.of("광주송정", "목포", "익산", "정읍", "나주", "공주", "서대전", "논산");
    private static final Set<String> JEOLLA_STATIONS = Set.of("여수엑스포", "여천", "순천", "전주", "남원", "곡성", "구례구");
    private static final Set<String> GANGNEUNG_STATIONS = Set.of("강릉", "평창", "진부", "정동진", "묵호", "동해", "만종", "횡성", "둔내");
    private static final Set<String> GYEONGJEON_STATIONS = Set.of("진주", "마산", "창원", "창원중앙", "진영");
    private static final Set<String> DONGHAE_STATIONS = Set.of("포항");
    private static final Set<String> JUNGANG_STATIONS = Set.of("안동", "영주", "풍기", "단양", "제천", "원주");

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

    /**
     * 출발역-도착역 기준으로 노선명(train.line_category) 자동 판별
     */
    public String resolveLineCategory(String depStationName, String arrStationName) {
        for (RoutePair pair : TARGET_ROUTES) {
            if (pair.getDepStationName().equals(depStationName) && pair.getArrStationName().equals(arrStationName)) {
                return pair.getLineCategory();
            }
        }
        if (isAnyIn(depStationName, arrStationName, JEOLLA_STATIONS)) return "전라선";
        if (isAnyIn(depStationName, arrStationName, HONAM_STATIONS)) return "호남선";
        if (isAnyIn(depStationName, arrStationName, GANGNEUNG_STATIONS)) return "강릉선";
        if (isAnyIn(depStationName, arrStationName, GYEONGJEON_STATIONS)) return "경전선";
        if (isAnyIn(depStationName, arrStationName, DONGHAE_STATIONS)) return "동해선";
        if (isAnyIn(depStationName, arrStationName, JUNGANG_STATIONS)) return "중앙선";
        return "경부선";
    }

    private boolean isAnyIn(String dep, String arr, Set<String> targetSet) {
        return (dep != null && targetSet.contains(dep)) || (arr != null && targetSet.contains(arr));
    }
}
