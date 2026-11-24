package com.kedu.tago.common.paging;

import lombok.Getter;

import java.util.List;

/** 페이징 조회 결과 */
@Getter
public class PageResult<T> {

    private final List<T> items;
    private final long totalCount;
    private final int page;
    private final int size;
    private final int totalPages;

    private PageResult(List<T> items, long totalCount, int page, int size) {
        this.items = items;
        this.totalCount = totalCount;
        this.page = page;
        this.size = size;
        this.totalPages = (int) Math.ceil((double) totalCount / size);
    }

    public static <T> PageResult<T> of(List<T> items, long totalCount, PageParam param) {
        return new PageResult<>(items, totalCount, param.getSafePage(), param.getSafeSize());
    }
}
