package com.kedu.tago.wishlist;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 찜하기 서비스 */
@Service
@RequiredArgsConstructor
public class WishListService {

    private final WishListMapper wishListMapper;

    /** 찜한 운행 일정 번호 목록 조회 */
    public List<Long> getWishList(String memberId) {
        return wishListMapper.selectWishList(memberId);
    }

    /** 찜 추가 */
    public int addWish(String memberId, Long scheduleSeq) {
        return wishListMapper.insertWish(memberId, scheduleSeq);
    }

    /** 찜 삭제 */
    public int deleteWish(String memberId, Long scheduleSeq) {
        return wishListMapper.deleteWish(memberId, scheduleSeq);
    }
}
