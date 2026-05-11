package com.itlab.domain.usecase.folderusecase

import com.itlab.domain.repository.NoteFolderRepository

class DeleteFolderUseCase(
    private val repo: NoteFolderRepository,
) {
    suspend operator fun invoke(id: String) {
        repo.deleteFolder(id)
    }
}
