package com.kedu.tago.wishlist;

import com.kedu.tago.common.response.ResWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 찜하기 API */
@RestController
@RequestMapping("/wishlist")
@RequiredArgsConstructor
public class WishListController {

    private final WishListService wishListService;

    /** 찜한 운행 일정 번호 목록 조회 (GET /api/wishlist) */
    @GetMapping
    public ResWrapper<List<Long>> getWishList(HttpServletRequest request) {
        String memberId = (String) request.getAttribute("id");
        return ResWrapper.list(wishListService.getWishList(memberId));
    }

    /** 찜 추가 (POST /api/wishlist/{scheduleSeq}) */
    @PostMapping("/{scheduleSeq}")
    public ResWrapper<Void> addWish(@PathVariable Long scheduleSeq, HttpServletRequest request) {
        String memberId = (String) request.getAttribute("id");
        wishListService.addWish(memberId, scheduleSeq);
        return ResWrapper.done("찜 목록에 추가했습니다.");
    }

    /** 찜 삭제 (DELETE /api/wishlist/{scheduleSeq}) */
    @DeleteMapping("/{scheduleSeq}")
    public ResWrapper<Void> deleteWish(@PathVariable Long scheduleSeq, HttpServletRequest request) {
        String memberId = (String) request.getAttribute("id");
        wishListService.deleteWish(memberId, scheduleSeq);
        return ResWrapper.done("찜 목록에서 삭제했습니다.");
    }
}
