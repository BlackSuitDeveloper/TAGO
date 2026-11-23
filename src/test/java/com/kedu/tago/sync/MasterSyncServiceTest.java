package com.kedu.tago.sync;

import com.kedu.tago.openapi.TagoApiClient;
import com.kedu.tago.openapi.TagoRouteConfig;
import com.kedu.tago.openapi.dto.TagoStationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 마스터 동기화: 열차 마스터 1회 적재, 역 캐시 */
class MasterSyncServiceTest {

    private MasterSyncMapper mapper;
    private MasterSyncService service;

    @BeforeEach
    void setUp() {
        mapper = mock(MasterSyncMapper.class);
        service = new MasterSyncService(mapper, mock(TagoApiClient.class), new TagoRouteConfig());
    }

    private static TagoStationDTO station(String id, String name) {
        return TagoStationDTO.builder().nodeid(id).nodename(name).build();
    }

    @Test
    @DisplayName("ensureKtxTrains: 여러 번 불러도 열차 마스터는 한 번만 저장한다")
    void ensureKtxTrainsRunsOnce() {
        when(mapper.upsertKtxTrains(anyList())).thenReturn(46);

        service.ensureKtxTrains();
        service.ensureKtxTrains();
        service.ensureKtxTrains();

        verify(mapper, times(1)).upsertKtxTrains(anyList());
    }

    @Test
    @DisplayName("ensureKtxTrains: 저장이 실패하면 다음 호출에서 다시 시도한다")
    void ensureKtxTrainsRetriesAfterFailure() {
        when(mapper.upsertKtxTrains(anyList()))
                .thenThrow(new IllegalStateException("DB 오류"))
                .thenReturn(46);

        assertThrows(IllegalStateException.class, () -> service.ensureKtxTrains());
        service.ensureKtxTrains();
        service.ensureKtxTrains();

        verify(mapper, times(2)).upsertKtxTrains(anyList());
    }

    @Test
    @DisplayName("역 캐시: 비어 있을 때만 DB 에서 읽고, 이후에는 다시 읽지 않는다")
    void stationCacheLoadsLazilyOnce() {
        when(mapper.selectAllStations()).thenReturn(List.of(station("NAT010000", "서울"), station("NAT014445", "부산")));

        assertEquals("NAT010000", service.getNodeIdByStationName("서울"));
        assertEquals("NAT014445", service.getNodeIdByStationName(" 부산 "));
        assertNull(service.getNodeIdByStationName("없는역"));
        assertNull(service.getNodeIdByStationName(null));

        verify(mapper, times(1)).selectAllStations();
    }

    @Test
    @DisplayName("역 캐시: 갱신하면 새 내용으로 통째로 바뀐다")
    void refreshReplacesCache() {
        when(mapper.selectAllStations())
                .thenReturn(List.of(station("A", "서울")))
                .thenReturn(List.of(station("B", "부산")));

        service.refreshStationCacheFromDb();
        Map<String, String> before = service.getStationNameToIdCache();
        service.refreshStationCacheFromDb();

        assertEquals(Map.of("서울", "A"), before); // 이전에 받은 맵은 바뀌지 않는다
        assertEquals(Map.of("부산", "B"), service.getStationNameToIdCache());
    }

    @Test
    @DisplayName("서비스 키가 없으면 외부 역 조회와 DB 저장을 하지 않는다")
    void syncStationsSkippedWithoutKey() {
        TagoApiClient client = mock(TagoApiClient.class);
        when(client.isServiceKeyConfigured()).thenReturn(false);
        MasterSyncService s = new MasterSyncService(mapper, client, new TagoRouteConfig());

        assertEquals(0, s.syncStationsFromTago());

        verify(client, never()).fetchCityCodes();
        verify(mapper, never()).upsertStations(anyList());
    }
}
