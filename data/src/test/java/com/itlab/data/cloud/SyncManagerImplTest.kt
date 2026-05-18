package com.itlab.data.cloud

import android.content.Context
import com.itlab.data.dao.MediaDao
import com.itlab.data.dao.NoteDao
import com.itlab.data.entity.MediaEntity
import com.itlab.data.entity.NoteEntity
import com.itlab.data.mapper.NoteEntityJsonConverter
import com.itlab.domain.cloud.CloudDataSource
import com.itlab.domain.cloud.CloudNoteMetadata
import com.itlab.domain.cloud.DomainFile
import com.itlab.domain.cloud.Result
import com.itlab.domain.cloud.SyncState
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Instant

class SyncManagerImplTest {
    @MockK
    lateinit var noteDao: NoteDao

    @MockK
    lateinit var mediaDao: MediaDao

    @MockK
    lateinit var cloudDataSource: CloudDataSource

    @MockK
    lateinit var jsonConverter: NoteEntityJsonConverter

    @MockK
    lateinit var context: Context

    private lateinit var syncManager: SyncManagerImpl
    private val now = Clock.System.now()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        // Сажаем тестовое дерево, чтобы безопасно поглощать логи Timber
        Timber.plant(
            object : Timber.Tree() {
                override fun log(
                    priority: Int,
                    tag: String?,
                    message: String,
                    t: Throwable?,
                ) {
                    // Ничего не делаем
                }
            },
        )

        coEvery { noteDao.getUnsyncedNotes(any()) } returns emptyList()
        coEvery { mediaDao.getUnsyncedMedia(any()) } returns emptyList()
        every { mediaDao.getAllMediaByUserId(any()) } returns flowOf(emptyList())

        coEvery { cloudDataSource.listMediaMetadata(any()) } returns Result.Success(emptyList())

        coEvery { cloudDataSource.uploadMedia(any(), any<com.itlab.domain.cloud.DomainFile>(), any()) } returns
            Result.Success(Unit)
        coEvery { cloudDataSource.downloadMedia(any(), any()) } returns Result.Success(Unit)

        syncManager = SyncManagerImpl(context, noteDao, mediaDao, cloudDataSource, jsonConverter)
    }

    @After
    fun tearDown() {
        Timber.uprootAll()
        unmockkAll()
    }

    @Test(expected = IOException::class)
    fun `sync should handle IOException`() =
        runBlocking {
            coEvery { noteDao.getUnsyncedNotes("user1") } throws IOException("No Internet")

            try {
                syncManager.sync("user1")
            } finally {
                val state = syncManager.syncState.value
                assertTrue(state is SyncState.Error)
                assertEquals("No Internet", (state as SyncState.Error).message)
            }
        }

    @Test(expected = SerializationException::class)
    fun `sync should handle SerializationException`() =
        runBlocking {
            coEvery { noteDao.getUnsyncedNotes("user1") } throws SerializationException("Bad JSON")

            try {
                syncManager.sync("user1")
            } finally {
                assertTrue(syncManager.syncState.value is SyncState.Error)
            }
        }

    @Test(expected = IllegalStateException::class)
    fun `sync should handle IllegalStateException`() =
        runBlocking {
            coEvery { noteDao.getUnsyncedNotes("user1") } throws IllegalStateException("Wrong state")

            try {
                syncManager.sync("user1")
            } finally {
                assertTrue(syncManager.syncState.value is SyncState.Error)
            }
        }

    @Test
    fun `pushChanges should throw and log on Result Error`() =
        runBlocking {
            val note = createTestNote("1")
            val exception = Exception("Upload Failed")

            coEvery { noteDao.getUnsyncedNotes(note.userId) } returns listOf(note)
            with(jsonConverter) { every { note.toJson() } returns "{}" }
            coEvery { cloudDataSource.uploadNote(any(), any()) } returns Result.Error(exception)

            val result = runCatching { syncManager.pushChanges("user1") }

            assertTrue(result.isFailure)
            assertEquals(exception, result.exceptionOrNull())
        }

    @Test
    fun `pullUpdates should throw and log when downloadNote fails`() =
        runBlocking {
            val meta = CloudNoteMetadata("note1", now)
            val exception = Exception("Download Failed")

            coEvery { cloudDataSource.listNoteMetadata(any()) } returns Result.Success(listOf(meta))
            every { noteDao.getAllNotesByUserId(any()) } returns flowOf(emptyList())
            coEvery { cloudDataSource.downloadNote("note1") } returns Result.Error(exception)

            val result = runCatching { syncManager.pullUpdates("user1") }

            assertTrue(result.isFailure)
            assertEquals(exception, result.exceptionOrNull())
        }

    @Test
    fun `pullUpdates should throw when listNoteMetadata fails`() =
        runBlocking {
            val exception = Exception("List Failed")
            coEvery { cloudDataSource.listNoteMetadata("user1") } returns Result.Error(exception)

            val result = runCatching { syncManager.pullUpdates("user1") }

            assertTrue(result.isFailure)
            assertEquals(exception, result.exceptionOrNull())
        }

    @Test
    fun `sync should complete full cycle with push and pull`() =
        runBlocking {
            val userId = "user1"
            val localNoteId = "local_1"
            val remoteNoteId = "remote_1"

            val expectedLocalPath = "users/$userId/notes/$localNoteId"
            val expectedRemotePath = "users/$userId/notes/$remoteNoteId"

            val unsyncedNote = createTestNote(localNoteId).copy(userId = userId, isSynced = false)
            coEvery { noteDao.getUnsyncedNotes(userId) } returns listOf(unsyncedNote)
            with(jsonConverter) {
                every { unsyncedNote.toJson() } returns "{\"id\":\"$localNoteId\"}"
            }
            coEvery { cloudDataSource.uploadNote(expectedLocalPath, any()) } returns Result.Success(Unit)
            coEvery { noteDao.update(any()) } just Runs

            val cloudMeta = CloudNoteMetadata(key = expectedRemotePath, updatedAt = now)
            coEvery { cloudDataSource.listNoteMetadata(userId) } returns Result.Success(listOf(cloudMeta))

            val localNote = createTestNote(localNoteId).copy(userId = userId, isSynced = true)
            every { noteDao.getAllNotesByUserId(any()) } returns flowOf(listOf(localNote))

            val remoteJson = "{\"id\":\"$remoteNoteId\"}"
            val remoteEntity = createTestNote(remoteNoteId).copy(userId = userId)

            coEvery { cloudDataSource.downloadNote(expectedRemotePath) } returns Result.Success(remoteJson)
            every { jsonConverter.toEntity(remoteJson, userId) } returns remoteEntity
            coEvery { noteDao.insert(remoteEntity) } just Runs

            syncManager.sync(userId)

            assertEquals(SyncState.Success, syncManager.syncState.value)

            coVerifyOrder {
                noteDao.getUnsyncedNotes(userId)
                mediaDao.getUnsyncedMedia(userId)

                cloudDataSource.uploadNote(expectedLocalPath, any())
                noteDao.update(match { it.id == localNoteId && it.isSynced })

                cloudDataSource.listNoteMetadata(userId)
                noteDao.getAllNotesByUserId(userId)
                cloudDataSource.downloadNote(expectedRemotePath)
                noteDao.insert(match { it.id == remoteNoteId })

                cloudDataSource.listMediaMetadata(userId)
                mediaDao.getAllMediaByUserId(userId)
            }
        }

    @Test
    fun `pullMedia should download new media and insert into dao`() =
        runBlocking {
            val userId = "user1"
            val noteId = "note1"
            val mediaId = "media1"
            val compositeId = "${noteId}_$mediaId"
            val cloudKey = "users/$userId/media/$compositeId"

            coEvery { cloudDataSource.listNoteMetadata(userId) } returns Result.Success(emptyList())
            every { noteDao.getAllNotesByUserId(userId) } returns flowOf(emptyList())

            val cloudMeta =
                com.itlab.domain.cloud.CloudMediaMetadata(
                    key = cloudKey,
                    mediaId = compositeId,
                    mimeType = "image/png",
                )
            coEvery { cloudDataSource.listMediaMetadata(userId) } returns Result.Success(listOf(cloudMeta))
            every { mediaDao.getAllMediaByUserId(userId) } returns flowOf(emptyList())
            coEvery { cloudDataSource.downloadMedia(eq(cloudKey), any()) } returns Result.Success(Unit)

            val mediaSlot = io.mockk.slot<com.itlab.data.entity.MediaEntity>()
            coEvery { mediaDao.insert(capture(mediaSlot)) } just Runs

            val tempDir =
                java.nio.file.Files
                    .createTempDirectory("test_media")
                    .toFile()
            every { context.filesDir } returns tempDir

            syncManager.pullUpdates(userId)

            assertTrue("Insert should be called", mediaSlot.isCaptured)
            val captured = mediaSlot.captured

            assertEquals("Media ID mismatch", mediaId, captured.id)
            assertEquals("Note ID mismatch", noteId, captured.noteId)
            assertTrue("Should be marked as synced", captured.isSynced)
            assertEquals("IMAGE", captured.type)

            tempDir.deleteRecursively()
            Unit
        }

    @Test
    fun `pullNotes should correctly extract noteId from full remote path and skip already existing notes`() =
        runBlocking {
            val userId = "user_123"
            val existingNoteId = "note_abc"

            val localNote = mockk<NoteEntity> { every { id } returns existingNoteId }
            every { noteDao.getAllNotesByUserId(userId) } returns flowOf(listOf(localNote))

            val remoteMetadata =
                listOf(
                    CloudNoteMetadata(
                        key = "users/$userId/notes/$existingNoteId",
                        updatedAt = Instant.fromEpochMilliseconds(1716037200000L),
                    ),
                )
            coEvery { cloudDataSource.listNoteMetadata(userId) } returns Result.Success(remoteMetadata)

            syncManager.pullUpdates(userId)

            coVerify(exactly = 0) {
                cloudDataSource.downloadNote(any())
            }
            Unit
        }

    @Test
    fun `pullNotes should download note even if another user has a local note with the same ID`() =
        runBlocking {
            val currentUserId = "user_current"
            val otherUserId = "user_alien"
            val duplicateNoteId = "shared_note_id"
            val remoteKey = "users/$currentUserId/notes/$duplicateNoteId"

            every { noteDao.getAllNotesByUserId(currentUserId) } returns flowOf(emptyList())

            val cloudMeta = CloudNoteMetadata(key = remoteKey, updatedAt = now)
            coEvery { cloudDataSource.listNoteMetadata(currentUserId) } returns Result.Success(listOf(cloudMeta))

            val remoteJson = "{\"id\":\"$duplicateNoteId\"}"

            val alienLocalNote =
                NoteEntity(
                    id = duplicateNoteId,
                    title = "Alien Private Note",
                    content = "Don't touch",
                    userId = otherUserId,
                    isSynced = false,
                    createdAt = now,
                    updatedAt = now,
                )

            every { noteDao.getAllNotesByUserId(otherUserId) } returns flowOf(listOf(alienLocalNote))

            val expectedEntity =
                NoteEntity(
                    id = duplicateNoteId,
                    title = "My Note",
                    content = "Content",
                    userId = currentUserId,
                    isSynced = true,
                    createdAt = now,
                    updatedAt = now,
                )
            coEvery { cloudDataSource.downloadNote(remoteKey) } returns Result.Success(remoteJson)
            every { jsonConverter.toEntity(remoteJson, currentUserId) } returns expectedEntity
            coEvery { noteDao.insert(expectedEntity) } just Runs

            syncManager.sync(currentUserId)

            coVerify(exactly = 1) {
                cloudDataSource.downloadNote(remoteKey)
                noteDao.insert(match { it.id == duplicateNoteId && it.userId == currentUserId })
            }
        }

    @Test
    fun `pullMedia should download media even if another user has a local media with the same ID`() =
        runBlocking {
            val currentUserId = "user_current"
            val otherUserId = "user_alien"
            val noteId = "note1"
            val mediaId = "duplicate_media_id"
            val compositeId = "${noteId}_$mediaId"
            val cloudKey = "users/$currentUserId/media/$compositeId"

            coEvery { cloudDataSource.listNoteMetadata(currentUserId) } returns Result.Success(emptyList())
            every { noteDao.getAllNotesByUserId(currentUserId) } returns flowOf(emptyList())

            val alienMedia =
                com.itlab.data.entity.MediaEntity(
                    id = mediaId,
                    noteId = "alien_note_id",
                    type = "audio",
                    localPath = "some/path",
                    remoteUrl = "users/$otherUserId/media/alien_note_id_$mediaId",
                    mimeType = "audio/mpeg",
                    size = 500L,
                    isSynced = true,
                )

            every { mediaDao.getAllMediaByUserId(currentUserId) } returns flowOf(emptyList())
            every { mediaDao.getAllMediaByUserId(otherUserId) } returns flowOf(listOf(alienMedia))

            val cloudMediaMeta =
                com.itlab.domain.cloud.CloudMediaMetadata(
                    key = cloudKey,
                    mediaId = compositeId,
                    mimeType = "image/png",
                )
            coEvery { cloudDataSource.listMediaMetadata(currentUserId) } returns Result.Success(listOf(cloudMediaMeta))
            coEvery { cloudDataSource.downloadMedia(eq(cloudKey), any()) } returns Result.Success(Unit)
            coEvery { mediaDao.insert(any()) } just Runs

            val tempDir =
                java.nio.file.Files
                    .createTempDirectory("test_media_pull")
                    .toFile()
            every { context.filesDir } returns tempDir

            syncManager.sync(currentUserId)

            coVerify(exactly = 1) {
                cloudDataSource.downloadMedia(eq(cloudKey), any())
                mediaDao.insert(match { it.id == mediaId && it.noteId == noteId })
            }

            tempDir.deleteRecursively()
            Unit
        }

    private fun createTestNote(id: String) =
        NoteEntity(
            id = id,
            title = "Title",
            content = "Content",
            userId = "user1",
            isSynced = false,
            createdAt = now,
            updatedAt = now,
        )
}
