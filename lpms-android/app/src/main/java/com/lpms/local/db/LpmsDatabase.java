package com.lpms.local.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

/**
 * The offline database.
 *
 * <p>Version 1 is the first release of offline mode, so there is no migration to write
 * and none to invent: a future release adds a {@code Migration} here rather than changing
 * the seed contract.</p>
 *
 * <p>Built by hand rather than by Hilt, because the login screen can enter offline mode
 * before any screen has drawn and none of the call sites may fail on a race.</p>
 */
@Database(entities = {DrugEntity.class, CategoryEntity.class, CustomerEntity.class,
        SaleEntity.class, SaleItemEntity.class, StockBatchEntity.class},
        version = 1, exportSchema = false)
@TypeConverters(Converters.class)
public abstract class LpmsDatabase extends RoomDatabase {

    private static final String NAME = "lpms_offline.db";

    /** Written once; the seeder checks {@link LpmsDao#drugCount()} before populating. */
    @Nullable
    private static volatile LpmsDatabase instance;

    public abstract LpmsDao dao();

    /**
     * Single instance for the whole process.
     *
     * <p>Built eagerly because the login screen can enter offline mode before any screen
     * has drawn, and none of the call sites are allowed to fail on a race here.</p>
     */
    @NonNull
    public static synchronized LpmsDatabase get(@NonNull Context context) {
        LpmsDatabase existing = instance;
        if (existing != null) {
            return existing;
        }
        LpmsDatabase created = Room.databaseBuilder(
                        context.getApplicationContext(),
                        LpmsDatabase.class,
                        NAME)
                // Offline preview data is disposable: it is seeded once and rebuilt from
                // the seeder if the user resets it. Losing it on a wipe is acceptable,
                // whereas silently keeping a stale price and quoting it later is not.
                .fallbackToDestructiveMigration()
                .build();
        instance = created;
        return created;
    }
}
