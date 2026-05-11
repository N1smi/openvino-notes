package com.itlab.data.mapper

import com.itlab.data.entity.NoteEntity
import com.itlab.domain.model.ContentItem
import com.itlab.domain.model.DataSource
import com.itlab.domain.model.Note
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class NoteMapperTest {
    private val mapper = NoteMapper()
    val testTime = Instant.parse("2026-03-24T12:00:00Z")

    @Test
    fun `toEntities should map all content types and media correctly`() {
        val note =
            Note(
                title = "Business",
                tags = setOf("money", "market"),
                contentItems =
                    listOf(
                        ContentItem.Text("I have money"),
                        ContentItem.Image(
                            source = DataSource(localPath = "local/path"),
                            mimeType = "image/png",
                        ),
                        ContentItem.File(
                            source = DataSource(remoteUrl = "https://cloud.com/doc"),
                            mimeType = "application/pdf",
                            name = "doc.pdf",
                            size = 1024L,
                        ),
                        ContentItem.Link("https://google.com"),
                    ),
                isFavorite = true,
                summary = "cars",
            )

        val (entity, media) = mapper.toEntities(note)

        assertEquals(note.id, entity.id)
        assertEquals("Business", entity.title)
        assertTrue(entity.isFavorite)
        assertFalse(entity.isSynced)
        assertEquals(null, entity.folderId)
        assertEquals(note.createdAt, entity.createdAt)
        assertEquals(note.updatedAt, entity.updatedAt)
        assertEquals(note.summary, entity.summary)

        assertEquals("[\"money\",\"market\"]", entity.tags)

        val decodedItems = Json.decodeFromString<List<ContentItem>>(entity.content)

        assertEquals(note.contentItems, decodedItems)

        assertEquals(2, media.size)

        val image = media.find { it.type == "IMAGE" }

        assertNotNull(image?.id)
        assertEquals(note.id, image?.noteId)
        assertEquals("local/path", image?.localPath)
        assertEquals(null, image?.remoteUrl)
        assertEquals("image/png", image?.mimeType)

        val file = media.find { it.type == "FILE" }
        assertNotNull(file?.id)
        assertEquals(note.id, file?.noteId)
        assertEquals("https://cloud.com/doc", file?.remoteUrl)
        assertEquals(null, file?.localPath)
        assertEquals("application/pdf", file?.mimeType)
        assertEquals(1024L, file?.size)
    }

    @Test
    fun `toDomain should return empty lists when JSON is corrupted`() {
        val corruptedEntity =
            NoteEntity(
                id = "test-id",
                title = "Broken Note",
                content = "!!not a json!!",
                tags = "{broken_tags}",
                createdAt = testTime,
                updatedAt = testTime,
            )

        val result = mapper.toDomain(corruptedEntity)

        assertTrue(result.contentItems.isEmpty())
        assertTrue(result.tags.isEmpty())
        assertEquals("Broken Note", result.title)
    }

    @Test
    fun `toDomain should correctly restore Note from NoteEntity`() {
        val originalItems =
            listOf(
                ContentItem.Text("First item"),
                ContentItem.Link("https://itlab.com", "IT Lab"),
                ContentItem.Image(
                    source = DataSource(localPath = "local/path"),
                    mimeType = "image/",
                ),
                ContentItem.File(
                    source = DataSource(remoteUrl = "https://cloud.com/doc"),
                    mimeType = "application/pdf",
                    name = "doc.pdf",
                    size = 1024L,
                ),
            )

        val originalTags = setOf("android", "testing")

        val json = Json { ignoreUnknownKeys = true }

        val entity =
            NoteEntity(
                id = "uuid-123",
                title = "Test Note",
                folderId = "fuid-100",
                content = json.encodeToString<List<ContentItem>>(originalItems),
                tags = json.encodeToString<Set<String>>(originalTags),
                isFavorite = true,
                createdAt = testTime,
                updatedAt = testTime,
            )

        val resultNote = mapper.toDomain(entity)

        assertEquals("uuid-123", resultNote.id)
        assertEquals("Test Note", resultNote.title)
        assertEquals("fuid-100", resultNote.folderId)
        assertTrue(resultNote.isFavorite)

        assertEquals(originalItems, resultNote.contentItems)
        assertEquals(originalTags, resultNote.tags)
    }

    @Test
    fun `toDomain should handle null tags in NoteEntity by returning empty set`() {
        val entityWithNullTags =
            NoteEntity(
                id = "test-null-tags",
                title = "Note with NULL tags",
                content = "[]",
                tags = null,
                folderId = null,
                isFavorite = false,
                createdAt = testTime,
                updatedAt = testTime,
            )

        val resultNote = mapper.toDomain(entityWithNullTags)

        assertNotNull(resultNote.tags)
        assertTrue(resultNote.tags.isEmpty())
    }
}
