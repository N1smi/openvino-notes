package com.itlab.domain.usecase.noteusecase

import com.itlab.domain.model.Note
import com.itlab.domain.repository.NotesRepository

class GetNoteUseCase(
    private val repo: NotesRepository,
) {
    suspend operator fun invoke(id: String): Note? = repo.getNoteById(id)
}
