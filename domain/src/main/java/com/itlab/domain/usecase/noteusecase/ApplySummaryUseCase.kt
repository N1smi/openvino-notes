package com.itlab.domain.usecase.noteusecase

import com.itlab.domain.repository.NotesRepository
import kotlin.time.Clock

class ApplySummaryUseCase(
    private val repo: NotesRepository,
) {
    suspend operator fun invoke(
        noteId: String,
        newSummary: String,
    ) {
        val note =
            repo.getNoteById(noteId)
                ?: throw IllegalArgumentException("Note not found")

        val updated =
            note.copy(
                summary = newSummary,
                updatedAt =
                    Clock.System
                        .now(),
            )

        repo.updateNote(updated)
    }
}
