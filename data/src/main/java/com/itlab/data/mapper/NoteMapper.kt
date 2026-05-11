package com.itlab.data.mapper

import com.itlab.data.entity.MediaEntity
import com.itlab.data.entity.NoteEntity
import com.itlab.domain.model.ContentItem
import com.itlab.domain.model.Note
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID

class NoteMapper(
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        },
) {
    fun toEntities(note: Note): Pair<NoteEntity, List<MediaEntity>> {
        val noteId = note.id

        val mediaEntities =
            note.contentItems.mapNotNull { item ->
                toMediaEntity(item, noteId)
            }

        val noteEntity =
            NoteEntity(
                id = noteId,
                title = note.title,
                folderId = note.folderId,
                content = json.encodeToString(note.contentItems),
                createdAt = note.createdAt,
                updatedAt = note.updatedAt,
                tags = json.encodeToString(note.tags),
                isFavorite = note.isFavorite,
                isSynced = false,
                summary = note.summary,
            )

        return noteEntity to mediaEntities
    }

    fun toDomain(entity: NoteEntity): Note {
        val items =
            try {
                json.decodeFromString<List<ContentItem>>(entity.content)
            } catch (e: SerializationException) {
                Timber.e(e, "Note content mapping failed for entity: ${entity.id}")
                emptyList()
            }

        val tags =
            try {
                json.decodeFromString<Set<String>>(entity.tags ?: "[]")
            } catch (e: SerializationException) {
                Timber.e(e, "Tags mapping failed for note ${entity.id}. Raw data: ${entity.tags}")
                emptySet()
            }

        return Note(
            id = entity.id,
            title = entity.title,
            contentItems = items,
            folderId = entity.folderId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            tags = tags,
            isFavorite = entity.isFavorite,
        )
    }

    private fun toMediaEntity(
        item: ContentItem,
        noteId: String,
    ): MediaEntity? {
        val (source, type, mimeType) =
            when (item) {
                is ContentItem.Image -> Triple(item.source, "IMAGE", item.mimeType)
                is ContentItem.File -> Triple(item.source, "FILE", item.mimeType)
                else -> return null
            }

        return MediaEntity(
            id = UUID.randomUUID().toString(),
            noteId = noteId,
            type = type,
            remoteUrl = source.remoteUrl,
            localPath = source.localPath,
            mimeType = mimeType,
            size = (item as? ContentItem.File)?.size,
        )
    }
}
