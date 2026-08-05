package com.neulbom.backend.common.api;

import java.util.List;

import org.springframework.data.domain.Page;

public record PageResponse<T>(
        List<T> items,
        long total,
        int page,
        int limit,
        boolean hasNext
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getTotalElements(),
                page.getNumber() + 1,
                page.getSize(),
                page.hasNext()
        );
    }
}
