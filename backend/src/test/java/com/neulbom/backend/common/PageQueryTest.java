package com.neulbom.backend.common;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import com.neulbom.backend.common.pagination.PageQuery;
import org.junit.jupiter.api.Test;

class PageQueryTest {

    @Test
    void rejectsReversedDateRange() {
        assertThatThrownBy(() -> new PageQuery(
                1,
                20,
                LocalDate.of(2026, 8, 6),
                LocalDate.of(2026, 8, 5)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from_date");
    }
}
