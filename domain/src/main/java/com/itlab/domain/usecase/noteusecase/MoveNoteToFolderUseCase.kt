package com.itlab.domain.usecase.noteusecase

import com.itlab.domain.repository.NotesRepository
import kotlin.time.Clock

class MoveNoteToFolderUseCase(
    private val notesRepo: NotesRepository,
) {
    suspend operator fun invoke(
        folderId: String,
        noteId: String,
    ) {
        val note = notesRepo.getNoteById(noteId) ?: throw IllegalArgumentException("Note not found: $noteId")
        val updated = note.copy(folderId = folderId, updatedAt = Clock.System.now())
        notesRepo.updateNote(updated)
    }
}
