package com.itlab.domain.usecase.contentusecase

import com.itlab.domain.model.ContentItem
import com.itlab.domain.repository.NotesRepository

class GetContentItemUseCase(
    private val notesRepository: NotesRepository,
) {
    suspend operator fun invoke(
        noteId: String,
        itemId: String,
    ): ContentItem? {
        val note =
            notesRepository.getNoteById(noteId)
                ?: throw IllegalArgumentException("Note not found: $noteId")

        return note.contentItems.find { it.id == itemId }
    }
}
