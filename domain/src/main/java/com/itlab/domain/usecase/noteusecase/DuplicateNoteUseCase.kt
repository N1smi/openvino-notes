package com.itlab.domain.usecase.noteusecase

import com.itlab.domain.model.ContentItem
import com.itlab.domain.repository.NotesRepository
import java.util.UUID
import kotlin.time.Clock

class DuplicateNoteUseCase(
    private val repo: NotesRepository,
) {
    suspend operator fun invoke(noteId: String): String {
        val note =
            repo.getNoteById(noteId)
                ?: throw IllegalArgumentException("Note not found: $noteId")

        val now = Clock.System.now()
        val duplicated =
            note.copy(
                id = UUID.randomUUID().toString(),
                title = if (note.title.isBlank()) "Copy" else "${note.title} Copy",
                createdAt = now,
                updatedAt = now,
                contentItems =
                    note.contentItems.map { item ->
                        when (item) {
                            is ContentItem.Text -> item.copy(id = UUID.randomUUID().toString())
                            is ContentItem.Image -> item.copy(id = UUID.randomUUID().toString())
                            is ContentItem.File -> item.copy(id = UUID.randomUUID().toString())
                            is ContentItem.Link -> item.copy(id = UUID.randomUUID().toString())
                        }
                    },
            )
        return repo.createNote(duplicated)
    }
}
