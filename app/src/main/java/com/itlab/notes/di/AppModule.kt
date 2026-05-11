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
