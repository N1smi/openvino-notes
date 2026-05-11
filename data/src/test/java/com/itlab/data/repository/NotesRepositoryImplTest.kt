package com.itlab.data.repository

import com.itlab.data.dao.MediaDao
import com.itlab.data.dao.NoteDao
import com.itlab.data.entity.NoteEntity
import com.itlab.data.mapper.NoteMapper
import com.itlab.domain.model.ContentItem
import com.itlab.domain.model.DataSource
import com.itlab.domain.model.Note
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Clock

class NotesRepositoryImplTest {
    private val noteDao = mockk<NoteDao>(relaxed = true)
    private val mediaDao = mockk<MediaDao>(relaxed = true)
    private val mapper = NoteMapper()
    private val repository = NotesRepositoryImpl(noteDao, mediaDao, mapper)

    @Test
    fun `createNote inserts note and media if exists`() =
        runTest {
            val note =
                Note(
                    id = "note_1",
                    title = "Test",
                    contentItems = emptyList(),
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now(),
                )

            repository.createNote(note)

            coEvery { noteDao.insert(any()) }
            coVerify(exactly = 0) { mediaDao.insertAll(any()) }
        }

    @Test
    fun `updateNote cleans old media and inserts new`() =
        runTest {
            val note =
                Note(
                    id = "note_1",
                    title = "Updated",
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now(),
                )

            repository.updateNote(note)

            coVerifyOrder {
                noteDao.update(any())
                mediaDao.deleteByNoteId("note_1")
            }
        }

    @Test
    fun `deleteNote deletes by entity from dao`() =
        runTest {
            val noteId = "1"
            coEvery { noteDao.getNoteByld(noteId) } returns mockk(relaxed = true)

            repository.deleteNote(noteId)
            coVerify { noteDao.delete(any()) }
        }

    @Test
    fun `observeNotes emits mapped list from dao`() =
        runTest {
            val entities = listOf(mockk<NoteEntity>(relaxed = true))
            coEvery { noteDao.getAllNotes() } returns flowOf(entities)

            val result = repository.observeNotes().first()

            assertEquals(1, result.size)
            coVerify { noteDao.getAllNotes() }
        }

    @Test
    fun `observeNotesByFolder emits filtered list`() =
        runTest {
            val folderId = "folder_x"
            coEvery { noteDao.getNotesByFolder(folderId) } returns flowOf(emptyList())

            val result = repository.observeNotesByFolder(folderId).first()

            assertTrue(result.isEmpty())
            coVerify { noteDao.getNotesByFolder(folderId) }
        }

    @Test
    fun `updateNote with media calls insertAll`() =
        runTest {
            val imageItem =
                ContentItem.Image(
                    source = DataSource(localPath = "some/path", remoteUrl = null),
                    mimeType = "image/png",
                )

            val noteWithMedia =
                Note(
                    id = "note_123",
                    title = "Note with Image",
                    contentItems = listOf(imageItem),
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now(),
                )

            repository.updateNote(noteWithMedia)

            coVerify { noteDao.update(any()) }
            coVerify { mediaDao.deleteByNoteId("note_123") }
            coVerify { mediaDao.insertAll(any()) }
        }

    @Test
    fun `updateNote without media should only call update and delete`() =
        runTest {
            val noteWithoutMedia = Note(id = "2", title = "No Media", contentItems = emptyList())

            repository.updateNote(noteWithoutMedia)

            coVerify { noteDao.update(any()) }
            coVerify { mediaDao.deleteByNoteId("2") }
            coVerify(exactly = 0) { mediaDao.insertAll(any()) }
        }

    @Test
    fun `deleteNote does nothing if note not found`() =
        runTest {
            coEvery { noteDao.getNoteByld("non_existent") } returns null

            repository.deleteNote("non_existent")

            coVerify(exactly = 0) { noteDao.delete(any()) }
        }

    @Test
    fun `getNoteById returns null correctly`() =
        runTest {
            coEvery { noteDao.getNoteByld("any") } returns null
            val result = repository.getNoteById("any")
            assertNull(result)
        }

    @Test
    fun `deleteNote should not call dao delete if note is null`() =
        runTest {
            coEvery { noteDao.getNoteByld("missing_id") } returns null

            repository.deleteNote("missing_id")

            coVerify(exactly = 0) { noteDao.delete(any()) }
        }

    @Test
    fun `observeNotesByFolder emits empty list and then content`() =
        runTest {
            val folderId = "folder_1"
            val flow = MutableStateFlow<List<NoteEntity>>(emptyList())
            coEvery { noteDao.getNotesByFolder(folderId) } returns flow

            val firstResult = repository.observeNotesByFolder(folderId).first()
            assertTrue(firstResult.isEmpty())

            val entity =
                mockk<NoteEntity>(relaxed = true) {
                    every { id } returns "n1"
                }
            flow.value = listOf(entity)
            val secondResult = repository.observeNotesByFolder(folderId).first()
            assertEquals(1, secondResult.size)
        }

    @Test
    fun `observeNotes emits list when dao has data`() =
        runTest {
            val entity = mockk<NoteEntity>(relaxed = true)
            coEvery { noteDao.getAllNotes() } returns flowOf(listOf(entity))

            val result = repository.observeNotes().first()

            assertEquals(1, result.size)
        }

    @Test
    fun `deleteNote handles missing note gracefully`() =
        runTest {
            coEvery { noteDao.getNoteByld("unknown") } returns null

            repository.deleteNote("unknown")

            coVerify(exactly = 0) { noteDao.delete(any()) }
        }

    @Test
    fun `getNoteById returns mapped domain note when entity exists`() =
        runTest {
            val noteId = "note_123"
            val entity = mockk<NoteEntity>(relaxed = true)

            coEvery { noteDao.getNoteByld(noteId) } returns entity

            val result = repository.getNoteById(noteId)

            assertNotNull(result)
        }

    @Test
    fun `saveNote inserts media entities when note has media`() =
        runTest {
            val imageItem =
                ContentItem.Image(
                    source = DataSource(localPath = "local/path.jpg", remoteUrl = null),
                    mimeType = "image/jpeg",
                )
            val noteWithMedia =
                Note(
                    id = "note_with_pic",
                    title = "Vacation",
                    contentItems = listOf(imageItem),
                )

            coEvery { noteDao.insert(any()) } just Runs
            coEvery { mediaDao.insertAll(any()) } just Runs

            repository.updateNote(noteWithMedia)

            coVerify(exactly = 1) { mediaDao.insertAll(any()) }
        }
}
