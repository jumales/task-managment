package com.demo.taskmanager.data.di

import com.demo.taskmanager.data.api.DeviceTokenApi
import com.demo.taskmanager.data.api.FileApi
import com.demo.taskmanager.data.api.NotificationApi
import com.demo.taskmanager.data.api.ReportingApi
import com.demo.taskmanager.data.api.SearchApi
import com.demo.taskmanager.data.api.TaskApi
import com.demo.taskmanager.data.api.UserApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

/**
 * Hilt module that creates each Retrofit API interface from the singleton [Retrofit] instance
 * provided by core-network's [NetworkModule].
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideTaskApi(retrofit: Retrofit): TaskApi = retrofit.create()

    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi = retrofit.create()

    @Provides
    @Singleton
    fun provideFileApi(retrofit: Retrofit): FileApi = retrofit.create()

    @Provides
    @Singleton
    fun provideSearchApi(retrofit: Retrofit): SearchApi = retrofit.create()

    @Provides
    @Singleton
    fun provideReportingApi(retrofit: Retrofit): ReportingApi = retrofit.create()

    @Provides
    @Singleton
    fun provideNotificationApi(retrofit: Retrofit): NotificationApi = retrofit.create()

    @Provides
    @Singleton
    fun provideDeviceTokenApi(retrofit: Retrofit): DeviceTokenApi = retrofit.create()
}
