package com.itlab.domain.usecase.folderusecase

import com.itlab.domain.model.NoteFolder
import com.itlab.domain.repository.NoteFolderRepository
import kotlinx.coroutines.flow.Flow

class ObserveFoldersUseCase(
    private val repo: NoteFolderRepository,
) {
    operator fun invoke(): Flow<List<NoteFolder>> = repo.observeFolders()
}
