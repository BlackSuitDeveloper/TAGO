package com.kedu.tago.sync.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kedu.tago.sync.config.TagoRouteConfig;
import com.kedu.tago.sync.dto.TagoCityDTO;
import com.kedu.tago.sync.dto.TagoStationDTO;
import com.kedu.tago.sync.dto.TagoTrainScheduleDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 공공데이터포털(TAGO) 열차정보 API 호출 클라이언트.
 * - RestClient 사용
 * - URI.create() 직접 주입 및 인코딩 상태 감지로 ServiceKey 이중 인코딩 방지
 * - KTX 차량종류 코드(trainGradeCode=00) 단독 조회
 */
@Slf4j
@Component
public class TagoApiClient {

    private static final String KTX_TRAIN_GRADE_CODE = "00";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final TagoRouteConfig routeConfig;
    private final String baseUrl;
    private final String encodedServiceKey;

    public TagoApiClient(
            @Value("${tago.api.base-url:https://apis.data.go.kr/1613000/TrainInfo}") String baseUrl,
            @Value("${tago.api.service-key:}") String serviceKey,
            TagoRouteConfig routeConfig) {
        this.restClient = RestClient.builder().build();
        this.objectMapper = new ObjectMapper();
        this.routeConfig = routeConfig;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.encodedServiceKey = normalizeServiceKey(serviceKey);
    }

    public boolean isServiceKeyConfigured() {
        return encodedServiceKey != null && !encodedServiceKey.isBlank();
    }

    /**
     * 공공데이터포털 ServiceKey 이중 인코딩 방지 정규화.
     * - 이미 '%'가 포함된 Encoding 키면 그대로 사용
     * - Decoding 키면 UTF-8 URL 인코딩 1회 수행 후 URI.create()에 직접 전달
     */
    private String normalizeServiceKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return "";
        }
        String trimmed = rawKey.trim();
        if (trimmed.contains("%")) {
            return trimmed;
        }
        return URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
    }

    /**
     * 1. 시/도 코드 목록 조회 (GetCtyCodeList)
     */
    public List<TagoCityDTO> fetchCityCodes() {
        String url = String.format(
                "%s/GetCtyCodeList?serviceKey=%s&_type=json&numOfRows=100&pageNo=1",
                baseUrl, encodedServiceKey
        );
        JsonNode itemNode = executeGetItems(url, "GetCtyCodeList");
        if (itemNode == null) {
            return Collections.emptyList();
        }

        List<TagoCityDTO> cities = new ArrayList<>();
        for (JsonNode node : toNodeList(itemNode)) {
            String citycode = node.path("citycode").asText("").trim();
            String cityname = node.path("cityname").asText("").trim();
            if (!citycode.isEmpty() && !cityname.isEmpty()) {
                cities.add(TagoCityDTO.builder()
                        .citycode(citycode)
                        .cityname(cityname)
                        .build());
            }
        }
        return cities;
    }

    /**
     * 2. 시/도별 기차역 목록 조회 (GetCtyAcctoTrainSttnList)
     */
    public List<TagoStationDTO> fetchStationsByCity(String citycode, String cityname) {
        String url = String.format(
                "%s/GetCtyAcctoTrainSttnList?serviceKey=%s&_type=json&numOfRows=500&pageNo=1&cityCode=%s",
                baseUrl, encodedServiceKey, URLEncoder.encode(citycode, StandardCharsets.UTF_8)
        );
        JsonNode itemNode = executeGetItems(url, "GetCtyAcctoTrainSttnList(" + cityname + ")");
        if (itemNode == null) {
            return Collections.emptyList();
        }

        List<TagoStationDTO> stations = new ArrayList<>();
        for (JsonNode node : toNodeList(itemNode)) {
            String nodeid = node.path("nodeid").asText("").trim();
            String nodename = node.path("nodename").asText("").trim();
            if (!nodeid.isEmpty() && !nodename.isEmpty()) {
                stations.add(TagoStationDTO.builder()
                        .nodeid(nodeid)
                        .nodename(nodename)
                        .citycode(citycode)
                        .cityname(cityname)
                        .nodeAddress(routeConfig.resolveStationAddress(nodename, cityname))
                        .build());
            }
        }
        return stations;
    }

    /**
     * 3. 출/도착지 기반 KTX 열차 운행 일정 조회 (GetStrtpntAlocFndTrainInfo)
     * - trainGradeCode=00 (KTX) 고정 호출
     */
    public List<TagoTrainScheduleDTO> fetchKtxSchedules(String depPlaceId, String arrPlaceId, String depPlandTime) {
        String url = String.format(
                "%s/GetStrtpntAlocFndTrainInfo?serviceKey=%s&_type=json&numOfRows=500&pageNo=1&depPlaceId=%s&arrPlaceId=%s&depPlandTime=%s&trainGradeCode=%s",
                baseUrl,
                encodedServiceKey,
                URLEncoder.encode(depPlaceId, StandardCharsets.UTF_8),
                URLEncoder.encode(arrPlaceId, StandardCharsets.UTF_8),
                URLEncoder.encode(depPlandTime, StandardCharsets.UTF_8),
                KTX_TRAIN_GRADE_CODE
        );

        JsonNode itemNode = executeGetItems(url,
                String.format("GetStrtpntAlocFndTrainInfo(%s->%s, %s)", depPlaceId, arrPlaceId, depPlandTime));
        if (itemNode == null) {
            return Collections.emptyList();
        }

        List<TagoTrainScheduleDTO> schedules = new ArrayList<>();
        for (JsonNode node : toNodeList(itemNode)) {
            int trainno = node.path("trainno").asInt(0);
            String traingradename = node.path("traingradename").asText("KTX").trim();
            String depplacename = node.path("depplacename").asText("").trim();
            String arrplacename = node.path("arrplacename").asText("").trim();
            String depTime = node.path("depplandtime").asText("").trim();
            String arrTime = node.path("arrplandtime").asText("").trim();
            int adultcharge = node.path("adultcharge").asInt(0);

            // KTX 계열 열차만 허용 (도메인 제약: vehiclekndnm = 'KTX')
            if (trainno > 0 && isKtxGrade(traingradename) && depTime.length() >= 12 && arrTime.length() >= 12) {
                schedules.add(TagoTrainScheduleDTO.builder()
                        .trainno(trainno)
                        .traingradename("KTX")
                        .depPlaceId(depPlaceId)
                        .depplacename(depplacename)
                        .arrPlaceId(arrPlaceId)
                        .arrplacename(arrplacename)
                        .depplandtime(depTime)
                        .arrplandtime(arrTime)
                        .adultcharge(adultcharge)
                        .build());
            }
        }
        return schedules;
    }

    private boolean isKtxGrade(String gradeName) {
        if (gradeName == null || gradeName.isBlank()) {
            return false;
        }
        // 순수 KTX(KTX-1, 18호차 935석 규격)만 유지 (KTX-산천, KTX-이음 등 제외)
        return "KTX".equalsIgnoreCase(gradeName.trim());
    }

    private JsonNode executeGetItems(String fullUrl, String apiName) {
        if (!isServiceKeyConfigured()) {
            log.warn("[TAGO API] TAGO_SERVICE_KEY가 설정되지 않아 {} 호출을 건너뜁니다.", apiName);
            return null;
        }
        try {
            URI uri = URI.create(fullUrl);
            String rawBody = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            if (rawBody == null || rawBody.isBlank()) {
                log.warn("[TAGO API] {} 응답 본문이 비어 있습니다.", apiName);
                return null;
            }

            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode header = root.path("response").path("header");
            String resultCode = header.path("resultCode").asText("");
            if (!"00".equals(resultCode) && !resultCode.isEmpty()) {
                log.error("[TAGO API] {} 오류 응답 - resultCode={}, resultMsg={}",
                        apiName, resultCode, header.path("resultMsg").asText(""));
                return null;
            }

            JsonNode itemsNode = root.path("response").path("body").path("items");
            if (itemsNode.isMissingNode() || itemsNode.isNull() || itemsNode.isTextual()) {
                return null;
            }
            JsonNode itemNode = itemsNode.path("item");
            if (itemNode.isMissingNode() || itemNode.isNull()) {
                return null;
            }
            return itemNode;
        } catch (Exception e) {
            log.error("[TAGO API] {} 호출 중 예외 발생: {}", apiName, e.getMessage());
            return null;
        }
    }

    private List<JsonNode> toNodeList(JsonNode itemNode) {
        if (itemNode == null || itemNode.isNull() || itemNode.isMissingNode()) {
            return Collections.emptyList();
        }
        List<JsonNode> result = new ArrayList<>();
        if (itemNode.isArray()) {
            itemNode.forEach(result::add);
        } else if (itemNode.isObject()) {
            result.add(itemNode);
        }
        return result;
    }
}
