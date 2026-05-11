package com.itlab.domain.usecase.noteusecase

import com.itlab.domain.model.Note
import com.itlab.domain.repository.NotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetAllFavoritesUseCase(
    private val repo: NotesRepository,
) {
    operator fun invoke(): Flow<List<Note>> =
        repo.observeNotes().map { notes ->
            notes.filter { it.isFavorite }
        }
}
