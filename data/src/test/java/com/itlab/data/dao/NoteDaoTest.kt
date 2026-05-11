package com.itlab.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itlab.data.db.AppDatabase
import com.itlab.data.entity.NoteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class NoteDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var noteDao: NoteDao

    val testTime = Instant.parse("2026-03-24T12:00:00Z")

    private fun createNote(
        id: String,
        title: String,
        updatedAt: Instant = Instant.fromEpochMilliseconds(0),
        isSynced: Boolean = true,
    ) = NoteEntity(
        id = id,
        title = title,
        content = "Content",
        createdAt = testTime,
        updatedAt = updatedAt,
        isSynced = isSynced,
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

        noteDao = database.noteDao()
    }

    @After
    fun cleanup() {
        database.close()
    }

    @Test
    fun `insert and getNoteById should return correct note`() =
        runTest {
            val note = createNote("1", "Title 1")
            noteDao.insert(note)

            val retrieved = noteDao.getNoteByld("1")

            assertNotNull(retrieved)
            assertEquals(note.id, retrieved?.id)
            assertEquals(note.title, retrieved?.title)
            assertEquals(note.content, retrieved?.content)
            assertEquals(note.isSynced, retrieved?.isSynced)
            assertEquals(note.updatedAt.toEpochMilliseconds(), retrieved?.updatedAt?.toEpochMilliseconds())
        }

    @Test
    fun `update should modify existing note`() =
        runTest {
            val note = createNote("1", "Original")
            noteDao.insert(note)

            val updated = note.copy(title = "Updated")
            noteDao.update(updated)

            val retrieved = noteDao.getNoteByld("1")
            assertEquals("Updated", retrieved?.title)
        }

    @Test
    fun `delete should remove note from database`() =
        runTest {
            val note = createNote("1", "To be deleted")
            noteDao.insert(note)
            noteDao.delete(note)

            val retrieved = noteDao.getNoteByld("1")
            assertNull(retrieved)
        }

    @Test
    fun `getAllNotes should return notes ordered by updatedAt descending`() =
        runTest {
            val oldNote = createNote("1", "Old", updatedAt = Instant.fromEpochMilliseconds(1000L))
            val newNote = createNote("2", "New", updatedAt = Instant.fromEpochMilliseconds(2000L))

            noteDao.insert(oldNote)
            noteDao.insert(newNote)

            val notes = noteDao.getAllNotes().first()

            assertEquals(2, notes.size)
            assertEquals("2", notes[0].id)
            assertEquals("1", notes[1].id)
        }

    @Test
    fun `insertAll should save multiple notes and replace on conflict`() =
        runTest {
            val note1 = createNote("1", "First")
            val note2 = createNote("2", "Second")

            noteDao.insertAll(listOf(note1, note2))

            val note1Updated = createNote("1", "First Updated")
            noteDao.insertAll(listOf(note1Updated))

            val notes = noteDao.getAllNotes().first()
            assertEquals(2, notes.size)
            assertTrue(notes.any { it.title == "First Updated" })
        }

    @Test
    fun `getUnsyncedNotes should only return notes where isSynced is false`() =
        runTest {
            val synced = createNote("1", "Synced", isSynced = true)
            val unsynced = createNote("2", "Unsynced", isSynced = false)

            noteDao.insert(synced)
            noteDao.insert(unsynced)

            val result = noteDao.getUnsyncedNotes()

            assertEquals(1, result.size)
            assertEquals("2", result[0].id)
        }
}
