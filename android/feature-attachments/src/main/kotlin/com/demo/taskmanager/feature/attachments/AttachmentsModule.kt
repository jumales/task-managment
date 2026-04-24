package com.demo.taskmanager.feature.attachments

import android.content.Context
import com.demo.taskmanager.data.repo.FileRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides [FileUploader] as a singleton — context and repositories are already singletons. */
@Module
@InstallIn(SingletonComponent::class)
object AttachmentsModule {

    @Provides
    @Singleton
    fun provideFileUploader(
        @ApplicationContext context: Context,
        fileRepository: FileRepository,
    ): FileUploader = FileUploader(context, fileRepository)
}
