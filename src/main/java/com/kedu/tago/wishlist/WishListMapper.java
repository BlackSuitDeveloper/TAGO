package com.kedu.tago.wishlist;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 찜하기 매퍼 */
@Mapper
public interface WishListMapper {

    /** 회원이 찜한 운행 일정 번호 목록 조회 */
    List<Long> selectWishList(@Param("memberId") String memberId);

    /** 찜 추가 */
    int insertWish(@Param("memberId") String memberId, @Param("scheduleSeq") Long scheduleSeq);

    /** 찜 삭제 */
    int deleteWish(@Param("memberId") String memberId, @Param("scheduleSeq") Long scheduleSeq);
}
