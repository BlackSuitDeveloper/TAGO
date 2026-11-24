package com.kedu.tago.sync;

import com.kedu.tago.openapi.TagoApiClient;
import com.kedu.tago.openapi.TagoRouteConfig;
import com.kedu.tago.openapi.dto.TagoTrainScheduleDTO;
import com.kedu.tago.sync.dto.ScheduleInsertDTO;
import com.kedu.tago.sync.dto.SyncResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 일정 동기화: 저장은 묶어서, 중복·잘못된 일정은 거르고, 외부 API 는 노선마다 한 번 */
class ScheduleSyncServiceTest {

    private static final DateTimeFormatter F14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int ROUTES = new TagoRouteConfig().getTargetRoutes().size();

    private ScheduleSyncMapper scheduleMapper;
    private TagoApiClient client;
    private MasterSyncService master;
    private ScheduleSyncService service;

    @BeforeEach
    void setUp() {
        scheduleMapper = mock(ScheduleSyncMapper.class);
        client = mock(TagoApiClient.class);
        master = mock(MasterSyncService.class);
        service = new ScheduleSyncService(scheduleMapper, client, new TagoRouteConfig(), master);

        when(client.isServiceKeyConfigured()).thenReturn(true);
        when(master.getStationNameToIdCache()).thenReturn(Map.of("서울", "NAT010000"));
        // 모든 역 이름에 ID 를 준다 (이름 기반)
        when(master.getNodeIdByStationName(anyString())).thenAnswer(inv -> "ID_" + inv.getArgument(0));
        // 저장 요청한 만큼 저장됐다고 응답
        when(scheduleMapper.insertSchedulesIfAbsent(anyList())).thenAnswer(inv -> ((List<?>) inv.getArgument(0)).size());
    }

    private static TagoTrainScheduleDTO schedule(String depId, String arrId, LocalDateTime dep, LocalDateTime arr, int trainno) {
        return TagoTrainScheduleDTO.builder()
                .trainno(trainno).traingradename("KTX")
                .depPlaceId(depId).depplacename("출발").arrPlaceId(arrId).arrplacename("도착")
                .depplandtime(dep.format(F14)).arrplandtime(arr.format(F14))
                .adultcharge(59800).build();
    }

    private static LocalDateTime tomorrow(int hour, int minute) {
        return LocalDateTime.now().plusDays(1).withHour(hour).withMinute(minute).withSecond(0).withNano(0);
    }

    @Test
    @DisplayName("서비스 키가 없으면 외부 호출도 저장도 하지 않는다")
    void doesNothingWithoutKey() {
        when(client.isServiceKeyConfigured()).thenReturn(false);

        SyncResultDTO result = service.syncSchedulesByDate("20261125");

        verify(client, never()).fetchKtxSchedules(anyString(), anyString(), anyString());
        verify(scheduleMapper, never()).insertSchedulesIfAbsent(anyList());
        assertTrue(result.getMessage().contains("TAGO_SERVICE_KEY"));
    }

    @Test
    @DisplayName("대상 노선마다 외부 API 를 한 번씩만 부르고, 일정은 한 번의 저장으로 묶는다")
    void oneApiCallPerRouteAndSingleBatchInsert() {
        when(client.fetchKtxSchedules(anyString(), anyString(), anyString())).thenAnswer(inv ->
                List.of(schedule(inv.getArgument(0), inv.getArgument(1), tomorrow(9, 0), tomorrow(11, 30), 101)));

        SyncResultDTO result = service.syncSchedulesByDate("20261125");

        verify(client, times(ROUTES)).fetchKtxSchedules(anyString(), anyString(), anyString());
        ArgumentCaptor<List<ScheduleInsertDTO>> saved = ArgumentCaptor.forClass(List.class);
        verify(scheduleMapper, times(1)).insertSchedulesIfAbsent(saved.capture());
        assertEquals(ROUTES, saved.getValue().size());
        assertEquals(ROUTES, result.getInsertedScheduleCount());
        assertEquals(0, result.getSkippedScheduleCount());
    }

    @Test
    @DisplayName("일정이 500건을 넘으면 500건씩 나눠 저장하고 순서를 유지한다")
    void insertsInChunksOf500InOrder() {
        // 노선 하나당 61건 x 10개 노선 = 610건 (출발 시각이 모두 달라 중복 없음)
        when(client.fetchKtxSchedules(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            List<TagoTrainScheduleDTO> list = new ArrayList<>();
            for (int i = 0; i < 61; i++) {
                LocalDateTime dep = LocalDateTime.now().plusDays(2).withHour(0).withMinute(0).withSecond(0).withNano(0).plusMinutes(i);
                list.add(schedule(inv.getArgument(0), inv.getArgument(1), dep, dep.plusHours(2), 101));
            }
            return list;
        });

        SyncResultDTO result = service.syncSchedulesByDate("20261126");

        ArgumentCaptor<List<ScheduleInsertDTO>> saved = ArgumentCaptor.forClass(List.class);
        verify(scheduleMapper, times(2)).insertSchedulesIfAbsent(saved.capture());
        assertEquals(500, saved.getAllValues().get(0).size());
        assertEquals(ROUTES * 61 - 500, saved.getAllValues().get(1).size());
        assertEquals(ROUTES * 61, result.getInsertedScheduleCount());
    }

    @Test
    @DisplayName("같은 일정이 겹치면 하나만 저장하고 나머지는 스킵으로 센다")
    void duplicatesAreSkipped() {
        when(client.fetchKtxSchedules(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            LocalDateTime dep = tomorrow(9, 0);
            return List.of(
                    schedule(inv.getArgument(0), inv.getArgument(1), dep, dep.plusHours(2), 101),
                    schedule(inv.getArgument(0), inv.getArgument(1), dep, dep.plusHours(2), 101));
        });

        SyncResultDTO result = service.syncSchedulesByDate("20261125");

        ArgumentCaptor<List<ScheduleInsertDTO>> saved = ArgumentCaptor.forClass(List.class);
        verify(scheduleMapper).insertSchedulesIfAbsent(saved.capture());
        assertEquals(ROUTES, saved.getValue().size());
        assertEquals(ROUTES, result.getInsertedScheduleCount());
        assertEquals(ROUTES, result.getSkippedScheduleCount());
    }

    @Test
    @DisplayName("이미 지난 일정, 도착이 출발보다 빠른 일정, 출발·도착이 같은 역인 일정은 저장하지 않는다")
    void invalidSchedulesAreFiltered() {
        when(client.fetchKtxSchedules(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            String dep = inv.getArgument(0);
            String arr = inv.getArgument(1);
            LocalDateTime now = LocalDateTime.now();
            return List.of(
                    schedule(dep, arr, now.minusHours(3), now.minusHours(1), 101),   // 이미 출발함
                    schedule(dep, arr, tomorrow(10, 0), tomorrow(9, 0), 102),        // 도착이 더 빠름
                    schedule(dep, dep, tomorrow(11, 0), tomorrow(13, 0), 103),       // 같은 역
                    schedule(dep, arr, tomorrow(12, 0), tomorrow(14, 0), 104));      // 정상
        });

        service.syncSchedulesByDate("20261125");

        ArgumentCaptor<List<ScheduleInsertDTO>> saved = ArgumentCaptor.forClass(List.class);
        verify(scheduleMapper).insertSchedulesIfAbsent(saved.capture());
        assertEquals(ROUTES, saved.getValue().size());
        assertTrue(saved.getValue().stream().allMatch(s -> s.getDepTime().getHour() == 12));
    }

    @Test
    @DisplayName("역 ID 를 찾지 못한 노선은 건너뛰고 나머지만 처리한다")
    void routesWithoutStationIdAreSkipped() {
        when(master.getNodeIdByStationName("부산")).thenReturn(null); // 부산이 들어간 노선(서울-부산, 부산-서울) 제외
        when(client.fetchKtxSchedules(anyString(), anyString(), anyString())).thenAnswer(inv ->
                List.of(schedule(inv.getArgument(0), inv.getArgument(1), tomorrow(9, 0), tomorrow(11, 0), 101)));

        service.syncSchedulesByDate("20261125");

        verify(client, times(ROUTES - 2)).fetchKtxSchedules(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("동기화마다 열차 마스터 보장을 요청한다 (실제 저장은 한 번만: MasterSyncServiceTest)")
    void requestsKtxTrainsEnsure() {
        when(client.fetchKtxSchedules(anyString(), anyString(), anyString())).thenReturn(List.of());

        service.syncSchedulesByDate("20261125");
        service.syncSchedulesByDate("20261126");

        verify(master, times(2)).ensureKtxTrains();
        verify(scheduleMapper, never()).insertSchedulesIfAbsent(any());
    }

    @Test
    @DisplayName("외부 API 가 빈 결과를 줘도 오류 없이 0건으로 끝난다")
    void emptyApiResultIsHandled() {
        when(client.fetchKtxSchedules(anyString(), anyString(), anyString())).thenReturn(List.of());

        SyncResultDTO result = service.syncSchedulesByDate("20261125");

        assertEquals(0, result.getInsertedScheduleCount());
        assertEquals(0, result.getSkippedScheduleCount());
        verify(client, times(ROUTES)).fetchKtxSchedules(anyString(), anyString(), anyString());
        verify(master, never()).syncStationsFromTago();
        verify(scheduleMapper, never()).insertSchedulesIfAbsent(anyList());
    }
}
