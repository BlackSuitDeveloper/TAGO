package com.kedu.tago.openapi;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.kedu.tago.openapi.dto.TagoTrainScheduleDTO;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TAGO API 클라이언트: 제한 시간, 재시도, 로그의 서비스 키 가림 (실제 로컬 HTTP 서버로 검증) */
class TagoApiClientTest {

    private static final String SECRET = "SECRETKEY+123/==";
    private static final String SECRET_ENCODED = "SECRETKEY%2B123%2F%3D%3D";
    private static final String PATH = "/TrainInfo/GetStrtpntAlocFndTrainInfo";

    private static final String OK_BODY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
             "body":{"items":{"item":[
               {"trainno":101,"traingradename":"KTX","depplacename":"서울","arrplacename":"부산",
                "depplandtime":202611250900,"arrplandtime":202611251135,"adultcharge":59800},
               {"trainno":202,"traingradename":"KTX-산천","depplacename":"서울","arrplacename":"부산",
                "depplandtime":202611251000,"arrplandtime":202611251235,"adultcharge":59800}
             ]}}}}
            """;

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger clientLogger = (Logger) LoggerFactory.getLogger(TagoApiClient.class);

    @BeforeEach
    void startLogCapture() {
        logs.start();
        clientLogger.addAppender(logs);
    }

    @AfterEach
    void cleanUp() {
        clientLogger.detachAppender(logs);
        if (server != null) {
            server.stop(0);
        }
    }

    /** attempt(1부터) 번째 요청에 대한 응답 코드를 정하는 서버를 띄운다. 본문은 200 일 때만 OK_BODY */
    private String startServer(IntFunction<Integer> statusByAttempt, long handlerDelayMs) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext(PATH, exchange -> {
            int attempt = requests.incrementAndGet();
            try {
                if (handlerDelayMs > 0) {
                    Thread.sleep(handlerDelayMs);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            int status = statusByAttempt.apply(attempt);
            byte[] body = (status == 200 ? OK_BODY : "{}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
            } catch (IOException ignored) {
                // 클라이언트가 제한 시간으로 먼저 끊은 경우
            } finally {
                exchange.close();
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/TrainInfo";
    }

    private TagoApiClient client(String baseUrl, long readTimeoutMs, int maxAttempts) {
        return new TagoApiClient(baseUrl, SECRET, 1000, readTimeoutMs, maxAttempts, 20, new TagoRouteConfig());
    }

    private List<TagoTrainScheduleDTO> fetch(TagoApiClient c) {
        return c.fetchKtxSchedules("NAT010000", "NAT014445", "20261125");
    }

    private String allLogText() {
        StringBuilder sb = new StringBuilder();
        for (ILoggingEvent e : logs.list) {
            sb.append(e.getFormattedMessage()).append('\n');
        }
        return sb.toString();
    }

    @Test
    @DisplayName("정상 응답: KTX 일정만 골라 돌려준다")
    void parsesKtxSchedulesOnly() throws Exception {
        String base = startServer(a -> 200, 0);
        List<TagoTrainScheduleDTO> result = fetch(client(base, 2000, 2));

        assertEquals(1, result.size());
        assertEquals(101, result.get(0).getTrainno());
        assertEquals(1, requests.get());
    }

    @Test
    @DisplayName("일시적 서버 오류(503) 뒤 성공하면 재시도해서 결과를 받는다")
    void retriesTransientServerError() throws Exception {
        String base = startServer(a -> a == 1 ? 503 : 200, 0);
        List<TagoTrainScheduleDTO> result = fetch(client(base, 2000, 2));

        assertEquals(1, result.size());
        assertEquals(2, requests.get());
    }

    @Test
    @DisplayName("계속 실패하면 정해진 횟수까지만 시도하고 빈 목록을 돌려준다")
    void givesUpAfterMaxAttempts() throws Exception {
        String base = startServer(a -> 503, 0);
        List<TagoTrainScheduleDTO> result = fetch(client(base, 2000, 3));

        assertTrue(result.isEmpty());
        assertEquals(3, requests.get());
    }

    @Test
    @DisplayName("요청 자체가 잘못된 경우(400)는 재시도하지 않는다")
    void doesNotRetryClientError() throws Exception {
        String base = startServer(a -> 400, 0);
        List<TagoTrainScheduleDTO> result = fetch(client(base, 2000, 3));

        assertTrue(result.isEmpty());
        assertEquals(1, requests.get());
    }

    @Test
    @DisplayName("429(요청 과다)는 재시도한다")
    void retriesTooManyRequests() throws Exception {
        String base = startServer(a -> a == 1 ? 429 : 200, 0);
        assertEquals(1, fetch(client(base, 2000, 2)).size());
        assertEquals(2, requests.get());
    }

    @Test
    @DisplayName("응답이 제한 시간보다 늦으면 기다리지 않고 포기한다")
    void readTimeoutIsEnforced() throws Exception {
        String base = startServer(a -> 200, 3000);
        long started = System.nanoTime();
        List<TagoTrainScheduleDTO> result = fetch(client(base, 300, 1));
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertTrue(result.isEmpty());
        assertTrue(elapsedMs < 2500, "제한 시간(300ms)을 넘겨 계속 기다렸다: " + elapsedMs + "ms");
    }

    @Test
    @DisplayName("제한 시간 초과도 일시적 오류로 보고 재시도한다")
    void timeoutIsRetried() throws Exception {
        String base = startServer(a -> 200, 3000);
        fetch(client(base, 300, 2));
        // 첫 요청이 늦어 끊긴 뒤 한 번 더 시도했는지 (서버 핸들러가 호출된 횟수)
        Thread.sleep(200);
        assertEquals(2, requests.get());
    }

    @Test
    @DisplayName("서비스 키가 없으면 외부 호출 자체를 하지 않는다")
    void skipsWhenServiceKeyMissing() throws Exception {
        String base = startServer(a -> 200, 0);
        TagoApiClient noKey = new TagoApiClient(base, "", 1000, 1000, 2, 20, new TagoRouteConfig());

        assertTrue(fetch(noKey).isEmpty());
        assertEquals(0, requests.get());
    }

    @Test
    @DisplayName("로그에 서비스 키가 남지 않는다 (HTTP 오류)")
    void serviceKeyNotLoggedOnHttpError() throws Exception {
        String base = startServer(a -> 500, 0);
        fetch(client(base, 2000, 2));

        String text = allLogText();
        assertFalse(text.isBlank(), "실패 로그가 남아야 한다");
        assertFalse(text.contains("SECRETKEY"), text);
        assertFalse(text.contains(SECRET_ENCODED), text);
    }

    @Test
    @DisplayName("로그에 서비스 키가 남지 않는다 (연결 실패: 예외 문구에 요청 주소가 들어가는 경우)")
    void serviceKeyNotLoggedOnConnectionFailure() throws Exception {
        String base = startServer(a -> 200, 0);
        server.stop(0); // 포트를 닫아 연결 실패를 만든다
        server = null;
        fetch(client(base, 500, 2));

        String text = allLogText();
        assertFalse(text.isBlank(), "실패 로그가 남아야 한다");
        assertFalse(text.contains("SECRETKEY"), text);
        assertFalse(text.contains(SECRET_ENCODED), text);
    }

    @Test
    @DisplayName("mask: 요청 주소의 serviceKey 값과 키 원문·인코딩 값을 모두 가린다")
    void maskHidesKeyInAnyForm() {
        TagoApiClient c = client("http://127.0.0.1:1/TrainInfo", 1000, 1);

        String viaQuery = c.mask("I/O error on GET request for \"http://x/TrainInfo/Get?serviceKey=" + SECRET_ENCODED + "&_type=json\": timeout");
        String viaEncoded = c.mask("failed: " + SECRET_ENCODED);
        String viaRaw = c.mask("failed: " + SECRET);

        for (String masked : new String[]{viaQuery, viaEncoded, viaRaw}) {
            assertFalse(masked.contains("SECRETKEY"), masked);
            assertTrue(masked.contains("****"), masked);
        }
        assertTrue(viaQuery.contains("&_type=json"), "키 이외의 내용은 그대로 둔다: " + viaQuery);
        assertEquals("", c.mask(null));
    }
}
