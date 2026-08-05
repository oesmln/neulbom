package com.neulbom.backend.common.pagination;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public record PageQuery(
        @Min(1) int page,
        @Min(1) @Max(100) int limit,
        LocalDate fromDate,
        LocalDate toDate
) {

    public PageQuery {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("from_date는 to_date보다 늦을 수 없습니다.");
        }
    }

    public Pageable pageable(Sort sort) {
        return PageRequest.of(page - 1, limit, sort);
    }
}
