package com.lpms.data.repo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.lpms.core.error.ApiErrorMapper;
import com.lpms.core.network.PageablePagingSource;
import com.lpms.data.api.AuditApi;
import com.lpms.data.dto.AuditLogResponse;
import com.lpms.data.dto.PageResponse;

import java.time.LocalDate;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit2.Call;

/**
 * Audit log. Read-only and ADMIN only.
 *
 * <p>The backend appends to this log itself on every write, so there is nothing to create
 * here - only ways to read it.</p>
 */
@Singleton
public final class AuditRepository {

    private final AuditApi auditApi;

    @Inject
    public AuditRepository(@NonNull AuditApi auditApi) {
        this.auditApi = auditApi;
    }

    /** Immutable filter; every field is optional and the server treats null as "any". */
    public static final class Filter {

        @Nullable
        final String action;
        @Nullable
        final Long userId;
        @Nullable
        final String entityType;
        @Nullable
        final LocalDate from;
        @Nullable
        final LocalDate to;

        public Filter(@Nullable String action, @Nullable Long userId,
                      @Nullable String entityType, @Nullable LocalDate from,
                      @Nullable LocalDate to) {
            this.action = action;
            this.userId = userId;
            this.entityType = entityType;
            this.from = from;
            this.to = to;
        }

        @NonNull
        public static Filter none() {
            return new Filter(null, null, null, null, null);
        }

        @Nullable
        public String getAction() {
            return action;
        }

        @Nullable
        public Long getUserId() {
            return userId;
        }

        @Nullable
        public String getEntityType() {
            return entityType;
        }

        @Nullable
        public LocalDate getFrom() {
            return from;
        }

        @Nullable
        public LocalDate getTo() {
            return to;
        }

        @NonNull
        public Filter withAction(@Nullable String value) {
            return new Filter(value, userId, entityType, from, to);
        }

        @NonNull
        public Filter withRange(@Nullable LocalDate newFrom, @Nullable LocalDate newTo) {
            return new Filter(action, userId, entityType, newFrom, newTo);
        }

        @NonNull
        public Filter cleared() {
            return none();
        }

        public boolean hasAnyFilter() {
            return action != null || userId != null || entityType != null
                    || from != null || to != null;
        }
    }

    private final class AuditPagingSource extends PageablePagingSource<AuditLogResponse> {

        private final Filter filter;

        AuditPagingSource(@NonNull Filter filter, @NonNull ApiErrorMapper mapper) {
            super(mapper, DEFAULT_PAGE_SIZE);
            this.filter = filter;
        }

        @NonNull
        @Override
        protected Call<PageResponse<AuditLogResponse>> callForPage(int page, int size) {
            return auditApi.list(filter.getAction(), filter.getUserId(), filter.getEntityType(),
                    filter.getFrom(), filter.getTo(), page, size, "createdAt,desc");
        }
    }

    @NonNull
    public PageablePagingSource<AuditLogResponse> paging(@NonNull Filter filter,
                                                        @NonNull ApiErrorMapper errorMapper) {
        return new AuditPagingSource(filter, errorMapper);
    }
}