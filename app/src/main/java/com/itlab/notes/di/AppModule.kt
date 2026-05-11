package com.itlab.notes.di

import com.itlab.domain.model.Note
import com.itlab.domain.model.NoteFolder
import com.itlab.domain.repository.NoteFolderRepository
import com.itlab.domain.repository.NotesRepository
import com.itlab.domain.usecase.folderusecase.CreateFolderUseCase
import com.itlab.domain.usecase.folderusecase.DeleteFolderUseCase
import com.itlab.domain.usecase.folderusecase.GetFolderUseCase
import com.itlab.domain.usecase.folderusecase.ObserveFoldersUseCase
import com.itlab.domain.usecase.folderusecase.UpdateFolderUseCase
import com.itlab.domain.usecase.noteusecase.CreateNoteUseCase
import com.itlab.domain.usecase.noteusecase.DeleteNoteUseCase
import com.itlab.domain.usecase.noteusecase.MoveNoteToFolderUseCase
import com.itlab.domain.usecase.noteusecase.ObserveNotesByFolderUseCase
import com.itlab.domain.usecase.noteusecase.UpdateNoteUseCase
import com.itlab.notes.ui.NotesUseCases
import com.itlab.notes.ui.NotesViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

// import com.itlab.notes.ui.NotesUseCases
// import com.itlab.notes.ui.NotesViewModel
// import org.koin.androidx.viewmodel.dsl.viewModel
val appModule =
    module {
        single<NotesRepository> { InMemoryNotesRepository() }
        single<NoteFolderRepository> { InMemoryFolderRepository() }

        factory { CreateNoteUseCase(get()) }
        factory { CreateFolderUseCase(get()) }
        factory { DeleteFolderUseCase(get()) }
        factory { DeleteNoteUseCase(get()) }
        factory { UpdateNoteUseCase(get()) }
        factory { UpdateFolderUseCase(get()) }
        factory { GetFolderUseCase(get()) }
        factory { ObserveNotesByFolderUseCase(get()) }
        factory { ObserveFoldersUseCase(get()) }
        factory { UpdateFolderUseCase(get()) }
        factory { GetFolderUseCase(get()) }
        factory { MoveNoteToFolderUseCase(get()) }
        factory {
            NotesUseCases(
                createFolderUseCase = get(),
                deleteFolderUseCase = get(),
                createNoteUseCase = get(),
                deleteNoteUseCase = get(),
                updateNoteUseCase = get(),
                observeNotesByFolderUseCase = get(),
                observeFoldersUseCase = get(),
                updateFolderUseCase = get(),
                getFolderUseCase = get(),
                moveNoteToFolderUseCase = get(),
            )
        }

        viewModel {
            NotesViewModel(
                useCases = get(),
            )
        }
    }

private class InMemoryNotesRepository : NotesRepository {
    private val notesFlow = MutableStateFlow<List<Note>>(emptyList())

    override fun observeNotes(): Flow<List<Note>> = notesFlow

    override fun observeNotesByFolder(folderId: String): Flow<List<Note>> =
        notesFlow.map { notes -> notes.filter { it.folderId == folderId } }

    override suspend fun getNoteById(id: String): Note? = notesFlow.value.firstOrNull { it.id == id }

    override suspend fun createNote(note: Note): String {
        notesFlow.value = notesFlow.value + note
        return note.id
    }

    override suspend fun updateNote(note: Note) {
        notesFlow.value =
            notesFlow.value.map { existing ->
                if (existing.id == note.id) note else existing
            }
    }

    override suspend fun deleteNote(id: String) {
        notesFlow.value = notesFlow.value.filterNot { it.id == id }
    }
}

private class InMemoryFolderRepository : NoteFolderRepository {
    private val foldersFlow =
        MutableStateFlow(
            listOf(
                NoteFolder(id = "all", name = "All Notes"),
                NoteFolder(id = "study", name = "My Study"),
                NoteFolder(id = "cook", name = "How to Cook"),
                NoteFolder(id = "poems", name = "My poems"),
            ),
        )

    override fun observeFolders(): Flow<List<NoteFolder>> = foldersFlow

    override suspend fun createFolder(folder: NoteFolder): String {
        foldersFlow.value = foldersFlow.value + folder
        return folder.id
    }

    override suspend fun renameFolder(
        id: String,
        name: String,
    ) {
        foldersFlow.value =
            foldersFlow.value.map { folder ->
                if (folder.id == id) folder.copy(name = name) else folder
            }
    }

    override suspend fun deleteFolder(id: String) {
        foldersFlow.value = foldersFlow.value.filterNot { it.id == id }
    }

    override suspend fun getFolderById(id: String): NoteFolder? = foldersFlow.value.firstOrNull { it.id == id }

    override suspend fun updateFolder(folder: NoteFolder) {
        foldersFlow.value =
            foldersFlow.value.map { existing ->
                if (existing.id == folder.id) folder else existing
            }
    }
}
