package com.itlab.domain.usecase.noteusecase

import com.itlab.domain.model.Note
import com.itlab.domain.repository.NotesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveNotesByFolderUseCase(
    private val repo: NotesRepository,
) {
    operator fun invoke(folderId: String?): Flow<List<Note>> =
        repo.observeNotes().map { notes ->
            if (folderId == null) notes else notes.filter { it.folderId == folderId }
        }
}
