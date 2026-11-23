package com.kedu.tago.common.paging;

import lombok.Data;

/**
 * 페이징 요청 공통 파라미터. 페이징이 필요한 검색 요청 DTO 가 상속해서 쓴다.
 *
 * <pre>
 * public class XxxSearchRequestDTO extends PageParam { ... }
 *
 * GET /xxx?page=2&amp;size=20  ->  page=2, size=20
 * </pre>
 *
 * page 는 1부터 시작한다. 잘못된 값은 안전한 값으로 보정한다(page &lt; 1 이면 1, size 가 0 이하면 기본값, 최대 {@value #MAX_SIZE}).
 * MyBatis 쿼리에서는 {@code LIMIT #{safeSize} OFFSET #{offset}} 으로 쓴다.
 */
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
