package com.kedu.tago.wishlist;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WishListMapper {

    List<Long> selectWishList(@Param("memberId") String memberId);

    int insertWish(@Param("memberId") String memberId, @Param("scheduleSeq") Long scheduleSeq);

    int deleteWish(@Param("memberId") String memberId, @Param("scheduleSeq") Long scheduleSeq);
}
