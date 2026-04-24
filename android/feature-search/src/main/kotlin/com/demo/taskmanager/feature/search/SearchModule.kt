package com.demo.taskmanager.feature.search

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.recentQueriesDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "recent_queries")

/**
 * Provides [DataStore]&lt;[Preferences]&gt; for [RecentQueriesStore].
 * Scoped to [SingletonComponent] so a single file is shared across the app lifecycle.
 */
@Module
@InstallIn(SingletonComponent::class)
object SearchModule {

    @Provides
    @Singleton
    fun provideRecentQueriesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.recentQueriesDataStore
}
