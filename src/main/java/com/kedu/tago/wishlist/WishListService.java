package com.kedu.tago.wishlist;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WishListService {

    private final WishListMapper wishListMapper;

    public List<Long> getWishList(String memberId) {
        return wishListMapper.selectWishList(memberId);
    }

    public int addWish(String memberId, Long scheduleSeq) {
        return wishListMapper.insertWish(memberId, scheduleSeq);
    }

    public int deleteWish(String memberId, Long scheduleSeq) {
        return wishListMapper.deleteWish(memberId, scheduleSeq);
    }
}
