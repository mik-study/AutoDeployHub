package com.proj.autodeploy.global.response;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 리스트 + 페이지 메타 응답. (05_api_spec.md §0.1)
 * { "data": [...], "page": { page, size, totalElements, totalPages } }
 */
public record PagedResponse<T>(List<T> data, PageMeta page) {

    public record PageMeta(int page, int size, long totalElements, int totalPages) {
    }

    public static <T> PagedResponse<T> of(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                new PageMeta(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages())
        );
    }
}
