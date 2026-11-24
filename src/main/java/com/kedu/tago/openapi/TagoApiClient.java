package com.kedu.tago.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kedu.tago.openapi.dto.TagoCityDTO;
import com.kedu.tago.openapi.dto.TagoStationDTO;
import com.kedu.tago.openapi.dto.TagoTrainScheduleDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 공공데이터포털(TAGO) 열차정보 API 호출 클라이언트
 * - 연결/읽기 제한 시간을 두어 외부 서버가 느려도 호출이 무한정 매달리지 않는다.
 * - 일시적 오류(연결 실패, 제한 시간 초과, 5xx, 429)만 짧게 기다렸다가 다시 시도한다. 그 외 4xx 는 재시도하지 않는다.
 * - 로그에는 서비스 키가 남지 않는다(요청 주소가 담긴 예외 문구도 가린다).
 * - 최종 실패는 예외 대신 null(목록 조회는 빈 목록)로 돌려주므로, 호출하는 쪽의 동작은 그대로다.
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
    private final String rawServiceKey;
    private final int maxAttempts;
    private final long retryBackoffMs;

    public TagoApiClient(
            @Value("${tago.api.base-url:https://apis.data.go.kr/1613000/TrainInfo}") String baseUrl,
            @Value("${tago.api.service-key:}") String serviceKey,
            @Value("${tago.api.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${tago.api.read-timeout-ms:10000}") long readTimeoutMs,
            @Value("${tago.api.max-attempts:2}") int maxAttempts,
            @Value("${tago.api.retry-backoff-ms:500}") long retryBackoffMs,
            TagoRouteConfig routeConfig) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.objectMapper = new ObjectMapper();
        this.routeConfig = routeConfig;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.encodedServiceKey = normalizeServiceKey(serviceKey);
        this.rawServiceKey = serviceKey == null ? "" : serviceKey.trim();
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBackoffMs = Math.max(0, retryBackoffMs);
    }

    /** ServiceKey 설정 여부 */
    public boolean isServiceKeyConfigured() {
        return encodedServiceKey != null && !encodedServiceKey.isBlank();
    }

    // 이중 인코딩 방지: 이미 '%' 포함이면 그대로, 아니면 1회 URL 인코딩
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

    /** 시/도 코드 목록 조회 (GetCtyCodeList) */
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

    /** 시/도별 기차역 목록 조회 (GetCtyAcctoTrainSttnList) */
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

    /** 출/도착지 기반 KTX(trainGradeCode=00) 운행 일정 조회 (GetStrtpntAlocFndTrainInfo) */
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

            // KTX 열차만 허용
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
        // 순수 KTX(KTX-1, 18호차 935석)만 유지 (KTX-산천, KTX-이음 등 제외)
        return "KTX".equalsIgnoreCase(gradeName.trim());
    }

    private JsonNode executeGetItems(String fullUrl, String apiName) {
        if (!isServiceKeyConfigured()) {
            log.warn("[TAGO API] TAGO_SERVICE_KEY가 설정되지 않아 {} 호출을 건너뜁니다.", apiName);
            return null;
        }
        try {
            String rawBody = fetchWithRetry(URI.create(fullUrl), apiName);

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
            log.error("[TAGO API] {} 처리 중 예외 발생: {}", apiName, mask(e.getMessage()));
            return null;
        }
    }

    // 일시적 오류만 최대 maxAttempts 번까지 시도한다. 최종 실패면 null
    private String fetchWithRetry(URI uri, String apiName) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String failure;
            boolean retryable;
            try {
                return restClient.get().uri(uri).retrieve().body(String.class);
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                retryable = status >= 500 || status == 429;
                failure = "HTTP " + status;
            } catch (ResourceAccessException e) {
                retryable = true;
                failure = "연결 실패 또는 제한 시간 초과 (" + mask(e.getMessage()) + ")";
            }

            if (!retryable || attempt == maxAttempts) {
                log.error("[TAGO API] {} 호출 실패 ({}번 시도): {}", apiName, attempt, failure);
                return null;
            }
            log.warn("[TAGO API] {} 호출 실패, 재시도합니다 ({}/{}): {}", apiName, attempt, maxAttempts, failure);
            if (!pauseBeforeRetry()) {
                return null;
            }
        }
        return null;
    }

    // 인터럽트되면 재시도를 멈춘다
    private boolean pauseBeforeRetry() {
        try {
            Thread.sleep(retryBackoffMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // 로그에 서비스 키가 남지 않게 가린다 (라이브러리 예외 문구에 요청 주소가 들어가는 경우 대비. 테스트에서 직접 호출)
    String mask(String text) {
        if (text == null) {
            return "";
        }
        String masked = text.replaceAll("(?i)(serviceKey=)[^&\\s\"')]*", "$1****");
        if (!encodedServiceKey.isEmpty()) {
            masked = masked.replace(encodedServiceKey, "****");
        }
        if (!rawServiceKey.isEmpty()) {
            masked = masked.replace(rawServiceKey, "****");
        }
        return masked;
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
