package com.itlab.domain.usecase.aiusecase

import com.itlab.domain.ai.NoteAiService
import com.itlab.domain.model.ContentItem
import com.itlab.domain.model.Note
import com.itlab.domain.repository.NotesRepository

class SuggestSummaryUseCase(
    private val ai: NoteAiService,
    private val repo: NotesRepository,
) {
    private fun extractText(note: Note): String =
        note.contentItems
            .filterIsInstance<ContentItem.Text>()
            .joinToString("\n") { it.text }

    suspend operator fun invoke(noteId: String): String {
        val note =
            repo.getNoteById(noteId)
                ?: throw IllegalArgumentException("Note not found: $noteId")

        val text = extractText(note)
        return ai.summarize(text)
    }
}
