package com.kedu.tago.common.paging;

import lombok.Data;

/** 페이징 요청 공통 파라미터 (page 는 1부터, 잘못된 값은 안전한 값으로 보정) */
@Data
public class PageParam {

    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 100;

    private int page = 1;
    private int size = DEFAULT_SIZE;

    public int getSafePage() {
        return Math.max(page, 1);
    }

    public int getSafeSize() {
        return size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }

    public int getOffset() {
        return (getSafePage() - 1) * getSafeSize();
    }
}
