package com.kedu.tago.wishlist;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/wishlist")
@RequiredArgsConstructor
public class WishListController {

    private final WishListService wishListService;

    @GetMapping
    public List<Long> getWishList(HttpServletRequest request) {
        String memberId = (String) request.getAttribute("id");
        return wishListService.getWishList(memberId);
    }

    @PostMapping("/{scheduleSeq}")
    public int addWish(@PathVariable Long scheduleSeq, HttpServletRequest request) {
        String memberId = (String) request.getAttribute("id");
        return wishListService.addWish(memberId, scheduleSeq);
    }

    @DeleteMapping("/{scheduleSeq}")
    public int deleteWish(@PathVariable Long scheduleSeq, HttpServletRequest request) {
        String memberId = (String) request.getAttribute("id");
        return wishListService.deleteWish(memberId, scheduleSeq);
    }
}
