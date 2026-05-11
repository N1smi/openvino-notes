package com.itlab.domain.usecase.noteusecase

import com.itlab.domain.repository.NotesRepository
import kotlin.time.Clock

class AddTagUseCase(
    private val repo: NotesRepository,
) {
    suspend operator fun invoke(
        noteId: String,
        tagToAdd: String,
    ) {
        val note =
            repo.getNoteById(noteId)
                ?: throw IllegalArgumentException("Note not found: $noteId")

        val updated =
            note.copy(
                tags = note.tags + tagToAdd,
                updatedAt = Clock.System.now(),
            )

        repo.updateNote(updated)
    }
}
