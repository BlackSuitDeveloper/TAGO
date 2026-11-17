package com.kedu.tago.schedule;

import lombok.RequiredArgsConstructor;
import org.apache.ibatis.session.SqlSession;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ScheduleDAO {

    private final SqlSession mybatis;

    /**
     * 전체 또는 조건부(출발역, 도착역, 날짜) 운행 일정 목록 조회
     */
    public List<ScheduleDTO> selectScheduleList(ScheduleSearchDTO searchDTO) {
        return mybatis.selectList("Schedule.selectScheduleList", searchDTO);
    }

    /**
     * 전체 또는 조건부 운행 일정 총 건수 조회
     */
    public long countScheduleList(ScheduleSearchDTO searchDTO) {
        Long count = mybatis.selectOne("Schedule.countScheduleList", searchDTO);
        return count != null ? count : 0L;
    }

    /**
     * 기차 일정 단건 상세 조회
     */
    public ScheduleDTO selectScheduleDetail(Long scheduleSeq) {
        return mybatis.selectOne("Schedule.selectScheduleDetail", scheduleSeq);
    }
}
