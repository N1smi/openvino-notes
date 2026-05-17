package com.itlab.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itlab.data.db.AppDatabase
import com.itlab.data.entity.MediaEntity
import com.itlab.data.entity.NoteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [34])
class MediaDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var mediaDao: MediaDao
    private lateinit var noteDao: NoteDao
    private val testUserId = "test_user_1"
    val testTime = Instant.parse("2026-03-24T12:00:00Z")

    private suspend fun insertParentNote(id: String) {
        val note =
            com.itlab.data.entity.NoteEntity(
                id = id,
                title = "Parent Note",
                content = "Content",
                createdAt = testTime,
                updatedAt = Instant.fromEpochMilliseconds(0),
                isSynced = true,
                userId = testUserId,
            )
        noteDao.insert(note)
    }

    private val defaultPath = """C:\Users\egoru\Downloads\Blazhin_-_Ne_perebivajj_64351892.mp3"""

    private fun createMedia(
        id: String,
        noteId: String,
        type: String = "audio",
        localPath: String? = defaultPath,
        remoteUrl: String? = null,
    ) = MediaEntity(
        id = id,
        noteId = noteId,
        type = type,
        remoteUrl = remoteUrl,
        localPath = localPath,
        mimeType = "audio/mpeg",
        size = 1024L,
    )

    @Before
    fun setup() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .build()

        mediaDao = database.mediaDao()
        noteDao = database.noteDao()
    }

    @After
    fun cleanup() {
        database.close()
    }

    @Test
    fun `insert and getMediaForNote should return correct media with paths`() =
        runTest {
            val noteId = "note1"
            insertParentNote(noteId)

            val audioPath = defaultPath
            val media = createMedia(id = "m1", noteId = "note1", localPath = audioPath)

            mediaDao.insert(media)

            val result = mediaDao.getMediaForNote("note1")

            assertEquals(1, result.size)
            assertEquals(audioPath, result[0].localPath)
            assertEquals("audio/mpeg", result[0].mimeType)
        }

    @Test
    fun `insertAll should handle list of media and replace on conflict`() =
        runTest {
            val noteId = "note1"
            insertParentNote(noteId)

            val list =
                listOf(
                    createMedia("m1", "note1"),
                    createMedia("m2", "note1"),
                )

            mediaDao.insertAll(list)

            val updatedMedia = createMedia("m1", "note1", remoteUrl = "https://s3.yandex.net/bucket/audio.mp3")
            mediaDao.insertAll(listOf(updatedMedia))

            val result = mediaDao.getMediaForNote("note1")
            val m1 = result.find { it.id == "m1" }

            assertEquals(2, result.size)
            assertEquals("https://s3.yandex.net/bucket/audio.mp3", m1?.remoteUrl)
        }

    @Test
    fun `getMediaForNoteFlow should notify about changes`() =
        runTest {
            val noteId = "note1"
            insertParentNote(noteId)

            val media = createMedia("m1", "note1")
            mediaDao.insert(media)

            val flowResult = mediaDao.getMediaForNoteFlow("note1").first()
            assertEquals(1, flowResult.size)
        }

    @Test
    fun `delete should remove specific media entity`() =
        runTest {
            val noteId = "note1"
            insertParentNote(noteId)

            val media = createMedia("m1", "note1")
            mediaDao.insert(media)
            mediaDao.delete(media)

            val result = mediaDao.getMediaForNote("note1")
            assertTrue(result.isEmpty())
        }

    @Test
    fun `getUnsyncedMedia should return only media with isSynced false`() =
        runTest {
            insertParentNote("note1")

            val syncedMedia = createMedia("m1", "note1").copy(isSynced = true)
            val unsyncedMedia = createMedia("m2", "note1").copy(isSynced = false)

            mediaDao.insert(syncedMedia)
            mediaDao.insert(unsyncedMedia)

            val result = mediaDao.getUnsyncedMedia()

            assertEquals(1, result.size)
            assertEquals("m2", result[0].id)
            assertEquals(false, result[0].isSynced)
        }

    @Test
    fun `deleteByNoteId should remove all media associated with note`() =
        runTest {
            insertParentNote("note1")
            insertParentNote("note2")

            mediaDao.insertAll(
                listOf(
                    createMedia("m1", "note1"),
                    createMedia("m2", "note1"),
                    createMedia("m3", "note2"),
                ),
            )

            mediaDao.deleteByNoteId("note1")

            val mediaNote1 = mediaDao.getMediaForNote("note1")
            val mediaNote2 = mediaDao.getMediaForNote("note2")

            assertTrue(mediaNote1.isEmpty())
            assertEquals(1, mediaNote2.size)
        }

    @Test
    fun `update should change sync status and remote url`() =
        runTest {
            insertParentNote("note1")
            val media = createMedia("m1", "note1").copy(isSynced = false, remoteUrl = null)
            mediaDao.insert(media)

            val updatedMedia = media.copy(isSynced = true, remoteUrl = "cloud://path/to/file")
            mediaDao.update(updatedMedia)

            val result = mediaDao.getMediaForNote("note1")[0]

            assertTrue(result.isSynced)
            assertEquals("cloud://path/to/file", result.remoteUrl)
        }

    @Test
    fun `getAllMedia should return all media from different notes`() =
        runTest {
            insertParentNote("note1")
            insertParentNote("note2")

            val media1 = createMedia("m1", "note1")
            val media2 = createMedia("m2", "note2")

            mediaDao.insert(media1)
            mediaDao.insert(media2)

            val result = mediaDao.getAllMedia().first()

            assertEquals(2, result.size)
            assertTrue(result.any { it.id == "m1" })
            assertTrue(result.any { it.id == "m2" })
        }

    @Test
    fun `deleteAll should clear media table`() =
        runTest {
            insertParentNote("note1")
            insertParentNote("note2")

            mediaDao.insertAll(
                listOf(
                    createMedia("m1", "note1"),
                    createMedia("m2", "note2"),
                ),
            )

            val beforeDelete = mediaDao.getAllMedia().first()
            assertEquals(2, beforeDelete.size)

            mediaDao.deleteAll()

            val afterDelete = mediaDao.getAllMedia().first()
            assertTrue("Table should be empty after deleteAll()", afterDelete.isEmpty())
        }

    @Test
    fun `getAllMedia flow should emit new list when data changes`() =
        runTest {
            insertParentNote("note1")

            val result1 = mediaDao.getAllMedia().first()
            assertTrue(result1.isEmpty())

            mediaDao.insert(createMedia("m1", "note1"))

            val result2 = mediaDao.getAllMedia().first()
            assertEquals(1, result2.size)
            assertEquals("m1", result2[0].id)
        }
}
