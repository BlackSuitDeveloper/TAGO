package com.kedu.tago.sync;

import com.kedu.tago.openapi.dto.TagoStationDTO;
import com.kedu.tago.openapi.dto.TagoTrainDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 마스터 데이터(station, train) 동기화 매퍼 (SQL: mapper/master_sync_mapper.xml) */
@Mapper
public interface MasterSyncMapper {

    /** 역 전체 건수 */
    int countStations();

    /** 역 전체 조회 */
    List<TagoStationDTO> selectAllStations();

    /** 역 일괄 upsert */
    int upsertStations(@Param("list") List<TagoStationDTO> stations);

    /** KTX 열차 일괄 upsert */
    int upsertKtxTrains(@Param("list") List<TagoTrainDTO> trains);
}
