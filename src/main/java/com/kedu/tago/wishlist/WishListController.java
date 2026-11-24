package com.kedu.tago.wishlist;

import com.kedu.tago.common.response.ResWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/wishlist")
@RequiredArgsConstructor
public class WishListController {

    private final WishListService wishListService;

    /** 찜한 운행 일정 번호 목록 */
    @GetMapping
    public ResWrapper<List<Long>> getWishList(HttpServletRequest request) {
        String memberId = (String) request.getAttribute("id");
        return ResWrapper.list(wishListService.getWishList(memberId));
    }

    @PostMapping("/{scheduleSeq}")
    public ResWrapper<Void> addWish(@PathVariable Long scheduleSeq, HttpServletRequest request) {
        String memberId = (String) request.getAttribute("id");
        wishListService.addWish(memberId, scheduleSeq);
        return ResWrapper.done("찜 목록에 추가했습니다.");
    }

    @DeleteMapping("/{scheduleSeq}")
    public ResWrapper<Void> deleteWish(@PathVariable Long scheduleSeq, HttpServletRequest request) {
        String memberId = (String) request.getAttribute("id");
        wishListService.deleteWish(memberId, scheduleSeq);
        return ResWrapper.done("찜 목록에서 삭제했습니다.");
    }
}
