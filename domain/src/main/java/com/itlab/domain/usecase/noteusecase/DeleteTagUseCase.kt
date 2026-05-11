package com.itlab.domain.usecase.noteusecase

import com.itlab.domain.repository.NotesRepository
import kotlin.time.Clock

class DeleteTagUseCase(
    private val repo: NotesRepository,
) {
    suspend operator fun invoke(
        noteId: String,
        tagToDel: String,
    ) {
        val note =
            repo.getNoteById(noteId)
                ?: throw IllegalArgumentException("Note not found: $noteId")

        val updated =
            note.copy(
                tags = note.tags - tagToDel,
                updatedAt = Clock.System.now(),
            )

        repo.updateNote(updated)
    }
}
