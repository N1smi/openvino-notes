package com.itlab.data.cloud

import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseException
import com.google.firebase.storage.FileDownloadTask
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ListResult
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import com.itlab.domain.cloud.Result
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

class FirebaseCloudDataSourceTest {
    @MockK
    lateinit var storage: FirebaseStorage

    @MockK
    lateinit var rootRef: StorageReference

    @MockK
    lateinit var childRef: StorageReference

    private lateinit var dataSource: FirebaseCloudDataSource

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        // Мокаем корутинный await() для Tasks
        mockkStatic("kotlinx.coroutines.tasks.TasksKt")

        every { storage.reference } returns rootRef
        dataSource = FirebaseCloudDataSource(storage)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `listNoteMetadata success`() =
        runBlocking {
            val userId = "user123"
            val listResult = mockk<ListResult>()
            val itemRef = mockk<StorageReference>()
            val metadata = mockk<StorageMetadata>()
            val taskList = mockk<Task<ListResult>>()
            val taskMetadata = mockk<Task<StorageMetadata>>()

            every { rootRef.child("users/$userId/notes") } returns childRef
            every { childRef.listAll() } returns taskList

            // Имитируем await()
            coEvery { taskList.await() } returns listResult
            every { listResult.items } returns listOf(itemRef)
            every { itemRef.path } returns "notes/note1.json"
            every { itemRef.metadata } returns taskMetadata
            coEvery { taskMetadata.await() } returns metadata
            every { metadata.updatedTimeMillis } returns 1672531200000L // 2023-01-01

            val result = dataSource.listNoteMetadata(userId)

            assertTrue(result is Result.Success)
            val data = (result as Result.Success).data
            assertEquals(1, data.size)
            assertEquals("notes/note1.json", data[0].key)
        }

    @Test
    fun `listMediaMetadata success`() =
        runBlocking {
            val userId = "user1"
            val listResult = mockk<ListResult>()
            val itemRef = mockk<StorageReference>()
            val metadata = mockk<StorageMetadata>()

            val taskList = mockk<Task<ListResult>>()
            val taskMeta = mockk<Task<StorageMetadata>>()

            every { rootRef.child("users/$userId/media") } returns childRef
            every { childRef.listAll() } returns taskList
            coEvery { taskList.await() } returns listResult

            every { listResult.items } returns listOf(itemRef)
            every { itemRef.path } returns "users/user1/media/note1_id1"
            every { itemRef.name } returns "note1_id1"
            every { itemRef.metadata } returns taskMeta

            coEvery { taskMeta.await() } returns metadata
            every { metadata.contentType } returns "image/png"

            val result = dataSource.listMediaMetadata(userId)

            assertTrue(result is Result.Success)
            val data = (result as Result.Success).data
            assertEquals("note1_id1", data[0].mediaId)
            assertEquals("image/png", data[0].mimeType)
        }

    @Test
    fun `downloadNote success`() =
        runBlocking {
            val key = "note_key"
            val bytes = "note content".toByteArray()
            val task = mockk<Task<ByteArray>>()

            every { rootRef.child(key) } returns childRef
            every { childRef.getBytes(any()) } returns task
            coEvery { task.await() } returns bytes

            val result = dataSource.downloadNote(key)

            assertTrue(result is Result.Success)
            assertEquals("note content", (result as Result.Success).data)
        }

    @Test
    fun `uploadNote success`() =
        runBlocking {
            val task = mockk<UploadTask>()
            every { rootRef.child(any()) } returns childRef
            every { childRef.putBytes(any()) } returns task
            coEvery { task.await() } returns mockk()

            val result = dataSource.uploadNote("key", "{}")

            assertTrue(result is Result.Success)
        }

    @Test
    fun `deleteNote success`() =
        runBlocking {
            val task = mockk<Task<Void>>()
            every { rootRef.child(any()) } returns childRef
            every { childRef.delete() } returns task
            coEvery { task.await() } returns mockk()

            val result = dataSource.deleteNote("key")

            assertTrue(result is Result.Success)
        }

    @Test
    fun `uploadMedia success`() {
        runBlocking {
            val file = File.createTempFile("test", "tmp")
            val task = mockk<UploadTask>()
            val mimeType = "image/jpeg"

            every { rootRef.child(any()) } returns childRef
            every { childRef.putStream(any(), any()) } returns task
            coEvery { task.await() } returns mockk()

            val result = dataSource.uploadMedia("key", file, mimeType)

            assertTrue(result is Result.Success)
            file.delete()
        }
    }

    @Test
    fun `downloadMedia success`() =
        runBlocking {
            val file = mockk<File>()
            val task = mockk<FileDownloadTask>()
            every { rootRef.child(any()) } returns childRef
            every { childRef.getFile(file) } returns task
            coEvery { task.await() } returns mockk()

            val result = dataSource.downloadMedia("key", file)

            assertTrue(result is Result.Success)
        }

    // ТЕСТЫ НА ОШИБКИ (ПОКРЫТИЕ safeCall)

    @Test
    fun `safeCall catches FirebaseException`() =
        runBlocking {
            val exception = mockk<FirebaseException>()
            every { rootRef.child(any()) } throws exception

            val result = dataSource.deleteNote("key")

            assertTrue(result is Result.Error)
            assertEquals(exception, (result as Result.Error).exception)
        }

    @Test
    fun `safeCall catches IOException`() =
        runBlocking {
            val exception = IOException("Disk error")
            every { rootRef.child(any()) } throws exception

            val result = dataSource.deleteNote("key")

            assertTrue(result is Result.Error)
        }

    @Test
    fun `safeCall catches generic Exception`() =
        runBlocking {
            every { rootRef.child(any()) } throws RuntimeException("Boom")

            val result = dataSource.deleteNote("key")

            assertTrue(result is Result.Error)
        }

    @Test(expected = CancellationException::class)
    fun `safeCall rethrows CancellationException`() {
        runBlocking {
            every { rootRef.child(any()) } throws CancellationException("Cancelled")
            dataSource.deleteNote("key")
        }
    }

    @Test
    fun `deleteMedia success`() =
        runBlocking {
            val key = "media/photo.jpg"
            val task = mockk<Task<Void>>()

            // Настраиваем цепочку: rootRef.child(key).delete()
            every { rootRef.child(key) } returns childRef
            every { childRef.delete() } returns task

            // Имитируем успешное завершение await()
            coEvery { task.await() } returns mockk()

            val result = dataSource.deleteMedia(key)

            // Проверяем результат
            assertTrue(result is Result.Success)
            verify { childRef.delete() }
        }

    @Test
    fun `deleteMedia failure`() =
        runBlocking {
            val key = "media/photo.jpg"
            // Используем реальное исключение вместо мока, чтобы safeCall его узнал
            val exception = RuntimeException("Firebase error")

            every { rootRef.child(key) } returns childRef
            // Эмулируем, что сам вызов childRef.delete() приводит к ошибке
            every { childRef.delete() } throws exception

            val result = dataSource.deleteMedia(key)

            // Проверяем, что safeCall поймал ошибку и вернул Result.Error
            assertTrue(result is Result.Error)
            assertEquals(exception, (result as Result.Error).exception)
        }
}
