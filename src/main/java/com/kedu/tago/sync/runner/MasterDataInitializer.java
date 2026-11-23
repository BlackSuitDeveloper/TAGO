package com.kedu.tago.sync.runner;

import com.kedu.tago.sync.client.TagoApiClient;
import com.kedu.tago.sync.dto.SyncResultDTO;
import com.kedu.tago.sync.service.MasterSyncService;
import com.kedu.tago.sync.service.ScheduleSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** 서버 기동 시 마스터 데이터 적재 및 운행 일정 동기화 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MasterDataInitializer implements ApplicationRunner {

    private final MasterSyncService masterSyncService;
    private final ScheduleSyncService scheduleSyncService;
    private final TagoApiClient tagoApiClient;

    @Value("${tago.sync.startup-enabled:true}")
    private boolean startupSyncEnabled;

    @Value("${tago.sync.sliding-window-days:3}")
    private int slidingWindowDays;

    @Override
    public void run(ApplicationArguments args) {
        if (!startupSyncEnabled) {
            log.info("[MasterDataInitializer] 초기 자동 동기화 비활성화 상태입니다 (tago.sync.startup-enabled=false).");
            return;
        }

        try {
            if (masterSyncService.isStationTableEmpty()) {
                if (!tagoApiClient.isServiceKeyConfigured()) {
                    log.warn("[MasterDataInitializer] station 테이블이 비어 있으나 TAGO_SERVICE_KEY가 설정되지 않아 초기 적재를 건너뜁니다. (.env에 TAGO_SERVICE_KEY 설정 후 POST /admin/sync/master 호출 가능)");
                    return;
                }
                log.info("[MasterDataInitializer] station 테이블이 비어 있어 공공데이터(TAGO) 정적 마스터 초기 적재를 시작합니다...");
                SyncResultDTO result = masterSyncService.syncAllMasterData();
                log.info("[MasterDataInitializer] 초기 적재 완료: {}", result.getMessage());
            } else {
                masterSyncService.refreshStationCacheFromDb();
                log.info("[MasterDataInitializer] 기존 마스터 데이터가 존재하여 역 캐시 워밍업만 수행했습니다.");
            }

            syncSchedulesOnStartupAsync();
        } catch (Exception e) {
            log.error("[MasterDataInitializer] 마스터 데이터 초기화 중 예외 발생 (테이블 생성 여부 또는 DB 연결 확인 필요): {}", e.getMessage());
        }
    }

    // 오늘부터 slidingWindowDays일 후까지의 운행 일정을 비동기로 갱신 (이미 있는 일정은 스킵)
    private void syncSchedulesOnStartupAsync() {
        if (!tagoApiClient.isServiceKeyConfigured()) {
            log.warn("[MasterDataInitializer] TAGO_SERVICE_KEY가 설정되지 않아 기동 시 운행 일정 동기화를 건너뜁니다.");
            return;
        }

        LocalDate startDate = LocalDate.now();
        LocalDate endDate = startDate.plusDays(Math.max(0, slidingWindowDays));

        CompletableFuture.runAsync(() -> {
            log.info("[MasterDataInitializer] 서버 기동 운행 일정(schedule) 동기화 시작: {} ~ {}", startDate, endDate);
            try {
                List<SyncResultDTO> results = scheduleSyncService.syncSchedulesByDateRange(startDate, endDate);
                for (SyncResultDTO r : results) {
                    log.info("[MasterDataInitializer] 기동 일정 동기화 결과 - {}", r.getMessage());
                }
                log.info("[MasterDataInitializer] 서버 기동 운행 일정(schedule) 동기화 완료");
            } catch (Exception e) {
                log.error("[MasterDataInitializer] 서버 기동 운행 일정 동기화 중 예외 발생: {}", e.getMessage(), e);
            }
        });
    }
}
