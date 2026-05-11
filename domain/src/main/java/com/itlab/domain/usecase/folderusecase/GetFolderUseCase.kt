package com.itlab.domain.usecase.folderusecase

import com.itlab.domain.model.NoteFolder
import com.itlab.domain.repository.NoteFolderRepository

class GetFolderUseCase(
    private val repo: NoteFolderRepository,
) {
    suspend operator fun invoke(id: String): NoteFolder? = repo.getFolderById(id)
}
