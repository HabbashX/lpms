package com.lpms.local.db;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A category as stored for offline mode; mirrors {@link com.lpms.data.dto.CategoryResponse}. */
@Entity(tableName = "categories")
public final class CategoryEntity {

    @PrimaryKey
    @ColumnInfo(name = "id")
    private final long id;

    @NonNull
    @ColumnInfo(name = "name")
    private final String name;

    /** Denormalised so a category row can be shown with its drug count. */
    @ColumnInfo(name = "drug_count")
    private final int drugCount;

    public CategoryEntity(long id, @NonNull String name, int drugCount) {
        this.id = id;
        this.name = name;
        this.drugCount = drugCount;
    }

    public long getId() {
        return id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public int getDrugCount() {
        return drugCount;
    }
}
