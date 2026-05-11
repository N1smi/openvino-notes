package com.itlab.domain.usecase.contentusecase

import com.itlab.domain.repository.NotesRepository
import kotlin.time.Clock

class DeleteContentItemUseCase(
    private val notesRepository: NotesRepository,
) {
    suspend operator fun invoke(
        noteId: String,
        itemId: String,
    ) {
        val note =
            notesRepository.getNoteById(noteId)
                ?: throw IllegalArgumentException("Note not found: $noteId")

        val updated =
            note.copy(
                contentItems = note.contentItems.filterNot { it.id == itemId },
                updatedAt = Clock.System.now(),
            )
        notesRepository.updateNote(updated)
    }
}
