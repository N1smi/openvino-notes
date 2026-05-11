package com.itlab.domain.usecase.contentusecase

import com.itlab.domain.model.ContentItem
import com.itlab.domain.repository.NotesRepository
import kotlin.time.Clock

class AddContentItemUseCase(
    private val notesRepository: NotesRepository,
) {
    suspend operator fun invoke(
        noteId: String,
        item: ContentItem,
    ) {
        require(item.id.isNotBlank()) {
            "ContentItem id must be created before adding to note"
        }

        val note =
            notesRepository.getNoteById(noteId)
                ?: throw IllegalArgumentException("Note not found: $noteId")

        val updated =
            note.copy(
                contentItems = note.contentItems + item,
                updatedAt = Clock.System.now(),
            )

        notesRepository.updateNote(updated)
    }
}
