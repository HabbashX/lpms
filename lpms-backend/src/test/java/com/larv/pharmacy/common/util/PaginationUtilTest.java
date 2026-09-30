package com.larv.pharmacy.common.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationUtilTest {

    private static final Set<String> ALLOWED = Set.of("id", "name");

    @Test
    void keepsValidSortProperty() {
        PageableResult result = sanitize(PageRequest.of(2, 10, Sort.by(Sort.Direction.DESC, "name")));
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.sortProperty()).isEqualTo("name");
        assertThat(result.sortDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void fallsBackToDefaultSortForUnknownProperty() {
        PageableResult result = sanitize(PageRequest.of(0, 10, Sort.by("passwordHash")));
        assertThat(result.sortProperty()).isEqualTo("id");
        assertThat(result.sortDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void clampsPageSizeToMaximum() {
        PageableResult result = sanitize(PageRequest.of(0, 5_000));
        assertThat(result.size()).isEqualTo(PaginationUtil.MAX_PAGE_SIZE);
    }

    @Test
    void normalizesUnpagedInput() {
        var sanitized = PaginationUtil.sanitize(org.springframework.data.domain.Pageable.unpaged(),
                ALLOWED, "id", Sort.Direction.ASC);
        assertThat(sanitized.getPageNumber()).isGreaterThanOrEqualTo(0);
        assertThat(sanitized.getPageSize()).isBetween(1, PaginationUtil.MAX_PAGE_SIZE);
        assertThat(sanitized.getSort().isSorted()).isTrue();
    }

    @Test
    void escapeLikeMakesWildcardsLiteral() {
        assertThat(PaginationUtil.escapeLike("100%_off\\path")).isEqualTo("100\\%\\_off\\\\path");
        assertThat(PaginationUtil.escapeLike(null)).isNull();
        assertThat(PaginationUtil.escapeLike("plain")).isEqualTo("plain");
    }

    private PageableResult sanitize(org.springframework.data.domain.Pageable pageable) {
        var sanitized = PaginationUtil.sanitize(pageable, ALLOWED, "id", Sort.Direction.ASC);
        var order = sanitized.getSort().iterator().next();
        return new PageableResult(sanitized.getPageNumber(), sanitized.getPageSize(),
                order.getProperty(), order.getDirection());
    }

    private record PageableResult(int page, int size, String sortProperty, Sort.Direction sortDirection) {
    }
}
