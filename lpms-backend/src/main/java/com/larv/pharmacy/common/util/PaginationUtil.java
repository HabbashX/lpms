package com.larv.pharmacy.common.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Set;

/**
 * Guards pagination input: clamps page sizes, rejects unknown sort properties
 * (which would otherwise surface as HTTP 500) and applies a sensible default sort.
 */
public final class PaginationUtil {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;

    private PaginationUtil() {
    }

    public static Pageable sanitize(Pageable pageable, Set<String> allowedSortProperties,
                                    String defaultSortProperty, Sort.Direction defaultDirection) {
        // Pageable.unpaged() throws for page/size/sort; treat those as "use defaults".
        int page = Math.max(safe(pageable::getPageNumber, 0), 0);
        int size = Math.min(Math.max(safe(pageable::getPageSize, DEFAULT_PAGE_SIZE), 1), MAX_PAGE_SIZE);

        Sort sort = Sort.unsorted();
        for (Sort.Order order : safe(pageable::getSort, Sort.unsorted())) {
            String property = order.getProperty();
            if (allowedSortProperties.contains(property)) {
                sort = sort.and(Sort.by(order.getDirection(), property));
            }
        }
        if (sort.isUnsorted()) {
            sort = Sort.by(defaultDirection, defaultSortProperty);
        }
        return PageRequest.of(page, size, sort);
    }

    private static <T> T safe(java.util.function.Supplier<T> supplier, T fallback) {
        try {
            T value = supplier.get();
            return value == null ? fallback : value;
        } catch (UnsupportedOperationException ex) {
            return fallback;
        }
    }

    public static String lower(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    /** Escapes LIKE wildcards in user input so searches stay literal. */
    public static String escapeLike(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
