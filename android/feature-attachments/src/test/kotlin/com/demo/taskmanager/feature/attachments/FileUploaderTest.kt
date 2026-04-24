package com.demo.taskmanager.feature.attachments

import android.content.Context
import android.net.Uri
import com.demo.taskmanager.data.repo.FileRepository
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.mockk.coVerify

/**
 * Tests [FileUploader] client-side size validation without touching the network.
 * [maxBytes] is set to 10 to avoid allocating large arrays in tests.
 */
class FileUploaderTest {

    private val fileRepository = mockk<FileRepository>(relaxed = true)

    /** Subclass that intercepts [getFileSize] to avoid needing a real ContentResolver. */
    private inner class FakeFileUploader(maxBytes: Long) :
        FileUploader(mockk(relaxed = true), fileRepository, maxBytes) {

        var stubbedSize: Long = 0L

        override fun getFileSize(uri: Uri): Long = stubbedSize
    }

    @Test
    fun `upload rejects file larger than maxBytes without calling repository`() = runTest {
        val uploader = FakeFileUploader(maxBytes = 10L).apply { stubbedSize = 11L }

        val result = uploader.upload(mockk<Uri>())

        assertTrue(result is UploadOutcome.Error)
        val error = (result as UploadOutcome.Error).error
        assertTrue(error is UploadError.FileTooLarge)
        assertEquals(10L, (error as UploadError.FileTooLarge).maxBytes)
        coVerify(exactly = 0) { fileRepository.uploadAttachment(any()) }
    }

    @Test
    fun `upload allows file exactly at limit`() = runTest {
        // FakeFileUploader returns size == maxBytes; size check is >, so exactly-at-limit passes
        // (upload will fail because openInputStream returns null from the relaxed mock,
        // producing UploadError.ReadFailed — but the size gate does NOT reject it)
        val uploader = FakeFileUploader(maxBytes = 10L).apply { stubbedSize = 10L }

        val result = uploader.upload(mockk<Uri>())

        // Should not be FileTooLarge
        val isFileTooLarge = result is UploadOutcome.Error &&
            (result as UploadOutcome.Error).error is UploadError.FileTooLarge
        assertTrue(!isFileTooLarge, "File exactly at limit should not be rejected by size check")
    }
}
