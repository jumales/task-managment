package com.demo.taskmanager.feature.attachments

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.demo.taskmanager.data.common.NetworkResult
import com.demo.taskmanager.data.dto.AttachmentDto
import com.demo.taskmanager.data.dto.AttachmentCreateRequest
import com.demo.taskmanager.data.dto.FileUploadDto
import com.demo.taskmanager.data.repo.TaskRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val taskRepository = mockk<TaskRepository>(relaxed = true)
    private val fileUploader = mockk<FileUploader>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { taskRepository.getAttachments(any()) } returns NetworkResult.Success(emptyList())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(taskId: String = "task-1") = AttachmentsViewModel(
        savedStateHandle = SavedStateHandle(mapOf("taskId" to taskId)),
        taskRepository = taskRepository,
        fileUploader = fileUploader,
        context = context,
    )

    @Test
    fun `initial state is loading`() = runTest {
        val vm = buildViewModel()
        vm.uiState.test {
            val initial = awaitItem()
            assertTrue(initial.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loads attachments on init`() = runTest {
        val attachments = listOf(attachmentDto("a-1"), attachmentDto("a-2"))
        coEvery { taskRepository.getAttachments("task-1") } returns NetworkResult.Success(attachments)

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.attachments.size)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `loadAttachments sets snackbar on HTTP error`() = runTest {
        coEvery { taskRepository.getAttachments(any()) } returns NetworkResult.Error(500, null)

        val vm = buildViewModel()
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.snackbarMessage)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `deleteAttachment reloads list on success`() = runTest {
        coEvery { taskRepository.deleteAttachment("task-1", "a-1") } returns NetworkResult.Success(Unit)

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.deleteAttachment("a-1")
        advanceUntilIdle()

        coVerify(exactly = 1) { taskRepository.deleteAttachment("task-1", "a-1") }
        // init + after-delete reload = 2 calls
        coVerify(exactly = 2) { taskRepository.getAttachments("task-1") }
    }

    @Test
    fun `deleteAttachment sets snackbar on error`() = runTest {
        coEvery { taskRepository.deleteAttachment(any(), any()) } returns NetworkResult.Error(422, null)

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.deleteAttachment("a-1")
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.snackbarMessage)
        // No reload on error
        coVerify(exactly = 1) { taskRepository.getAttachments("task-1") }
    }

    @Test
    fun `uploadAttachment sets snackbar when file is too large`() = runTest {
        val uri = mockk<Uri>()
        coEvery { fileUploader.upload(uri) } returns
            UploadOutcome.Error(UploadError.FileTooLarge(MAX_ATTACHMENT_BYTES))

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.uploadAttachment(uri)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.snackbarMessage)
        assertTrue(vm.uiState.value.snackbarMessage!!.contains("MB"))
        assertFalse(vm.uiState.value.isUploading)
        coVerify(exactly = 0) { taskRepository.addAttachment(any(), any()) }
    }

    @Test
    fun `uploadAttachment links file to task on success`() = runTest {
        val uri = mockk<Uri>()
        val fileDto = FileUploadDto("file-1", "attachments", "key/file-1", "application/pdf")
        val attachmentDto = attachmentDto("a-new")
        coEvery { fileUploader.upload(uri) } returns
            UploadOutcome.Success(fileDto, "report.pdf", "application/pdf")
        coEvery { taskRepository.addAttachment("task-1", any()) } returns
            NetworkResult.Success(attachmentDto)

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.uploadAttachment(uri)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isUploading)
        assertEquals(1, vm.uiState.value.attachments.size)
        coVerify(exactly = 1) { taskRepository.addAttachment("task-1", any()) }
    }

    @Test
    fun `clearSnackbar removes message`() = runTest {
        coEvery { taskRepository.deleteAttachment(any(), any()) } returns NetworkResult.Error(500, null)

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.deleteAttachment("a-1")
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.snackbarMessage)

        vm.clearSnackbar()
        assertNull(vm.uiState.value.snackbarMessage)
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private fun attachmentDto(id: String) = AttachmentDto(
        id = id,
        fileId = "file-$id",
        fileName = "test.pdf",
        contentType = "application/pdf",
        uploadedByUserId = "user-1",
        uploadedByUserName = "Alice",
        uploadedAt = "2024-01-01T00:00:00Z",
    )
}
