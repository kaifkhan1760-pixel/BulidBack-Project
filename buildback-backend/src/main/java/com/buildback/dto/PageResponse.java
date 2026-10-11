package com.buildback.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Stable JSON shape for one page of results (Spring's own Page JSON is not a stable API).
 * page is zero-based: the first page is 0.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }
}
