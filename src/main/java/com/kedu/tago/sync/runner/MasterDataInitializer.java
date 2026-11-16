package com.kedu.tago.sync.runner;

import com.kedu.tago.sync.client.TagoApiClient;
import com.kedu.tago.sync.dto.SyncResultDTO;
import com.kedu.tago.sync.service.MasterSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MasterDataInitializer implements ApplicationRunner {

    private final MasterSyncService masterSyncService;
    private final TagoApiClient tagoApiClient;

    @Value("${tago.sync.startup-enabled:true}")
    private boolean startupSyncEnabled;

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
        } catch (Exception e) {
            log.error("[MasterDataInitializer] 마스터 데이터 초기화 중 예외 발생 (테이블 생성 여부 또는 DB 연결 확인 필요): {}", e.getMessage());
        }
    }
}
