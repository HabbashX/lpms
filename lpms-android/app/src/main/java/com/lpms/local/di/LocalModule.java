package com.lpms.local.di;

import android.content.Context;

import androidx.annotation.NonNull;

import com.lpms.local.LocalBackend;
import com.lpms.local.db.LpmsDao;
import com.lpms.local.db.LpmsDatabase;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

/**
 * The offline graph.
 *
 * <p>Built here rather than by the repositories themselves so a test can supply an
 * in-memory database. {@link LpmsDatabase#get} keeps one instance per process, because
 * offline mode can be entered from the login screen before any screen has drawn.</p>
 */
@Module
@InstallIn(SingletonComponent.class)
public final class LocalModule {

    private LocalModule() {
    }

    @Provides
    @Singleton
    public static LpmsDatabase provideLpmsDatabase(@ApplicationContext Context context) {
        return LpmsDatabase.get(context);
    }

    @Provides
    @Singleton
    public static LpmsDao provideLpmsDao(@NonNull LpmsDatabase db) {
        return db.dao();
    }

    @Provides
    @Singleton
    public static LocalBackend provideLocalBackend(@NonNull LpmsDao dao) {
        return new LocalBackend(dao);
    }
}
