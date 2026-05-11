package com.itlab.domain.usecase.noteusecase

import com.itlab.domain.repository.NotesRepository

class SwitchFavoriteUseCase(
    private val repo: NotesRepository,
) {
    suspend operator fun invoke(noteId: String) {
        val note =
            repo.getNoteById(noteId)
                ?: throw IllegalArgumentException("Note not found")

        val updatedNote =
            note.copy(
                isFavorite = !note.isFavorite,
            )

        repo.updateNote(updatedNote)
    }
}
