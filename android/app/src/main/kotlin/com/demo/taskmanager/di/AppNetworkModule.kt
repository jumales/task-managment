package com.demo.taskmanager.di

import com.demo.taskmanager.BuildConfig
import com.demo.taskmanager.core.network.network.NetworkModule
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named

/**
 * Provides flavour-specific network configuration (base URL, Keycloak issuer) to [NetworkModule].
 * Values come from [BuildConfig], which is generated per product flavour at compile time.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppNetworkModule {

    @Provides
    @Named(NetworkModule.BASE_URL)
    fun provideBaseUrl(): String = BuildConfig.BASE_URL

    @Provides
    @Named(NetworkModule.KEYCLOAK_ISSUER)
    fun provideKeycloakIssuer(): String = BuildConfig.KEYCLOAK_ISSUER
}
