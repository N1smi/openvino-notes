package com.itlab.domain.usecase.noteusecase

import com.itlab.domain.repository.NotesRepository
import kotlin.time.Clock

class ApplyTagsUseCase(
    private val repo: NotesRepository,
) {
    suspend operator fun invoke(
        noteId: String,
        newTags: Set<String>,
    ) {
        val note =
            repo.getNoteById(noteId)
                ?: throw IllegalArgumentException("Note not found")

        val updated =
            note.copy(
                tags = newTags,
                updatedAt =
                    Clock.System
                        .now(),
            )

        repo.updateNote(updated)
    }
}
