package com.lpms.data.dto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

/**
 * The uniform pagination envelope every paged endpoint returns.
 *
 * <pre>
 * { "content":[...], "page":0, "size":20, "totalElements":134, "totalPages":7 }
 * </pre>
 *
 * <p>{@code page} is 0-based. The server clamps {@code size} to a maximum of 100,
 * so {@link #getSize()} reflects what was actually applied, not necessarily what was
 * requested.</p>
 */
public final class PageResponse<T> {

    @SerializedName("content")
    private final List<T> content;

    @SerializedName("page")
    private final int page;

    @SerializedName("size")
    private final int size;

    @SerializedName("totalElements")
    private final long totalElements;

    @SerializedName("totalPages")
    private final int totalPages;

    public PageResponse(@Nullable List<T> content,
                        int page,
                        int size,
                        long totalElements,
                        int totalPages) {
        this.content = content == null ? Collections.emptyList() : content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    /** Never null: an absent content array means "no rows". */
    @NonNull
    public List<T> getContent() {
        return content;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public boolean isEmpty() {
        return content.isEmpty();
    }

    /** Last page index, or 0 when there is nothing to page. */
    public int lastPage() {
        return totalPages <= 0 ? 0 : totalPages - 1;
    }
}