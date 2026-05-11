package com.itlab.domain.usecase.noteusecase

import com.itlab.domain.repository.NotesRepository

class DeleteNoteUseCase(
    private val repo: NotesRepository,
) {
    suspend operator fun invoke(noteId: String) {
        repo.deleteNote(noteId)
    }
}
