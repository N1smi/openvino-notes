package com.itlab.notes.ui

import androidx.compose.runtime.Composable
import com.itlab.notes.ui.editor.editorScreen
import com.itlab.notes.ui.notes.NotesListActions
import com.itlab.notes.ui.notes.directoriesScreen
import com.itlab.notes.ui.notes.notesListScreen
import org.koin.androidx.compose.koinViewModel

@Composable
fun notesApp() {
    val viewModel: NotesViewModel = koinViewModel()
    val state = viewModel.uiState

    when (val screen = state.screen) {
        NotesUiScreen.Directories -> {
            directoriesScreen(
                directories = state.directories,
                onCreateDirectory = { name ->
                    viewModel.onEvent(NotesUiEvent.CreateDirectory(name))
                },
                onDeleteDirectory = { directory ->
                    viewModel.onEvent(NotesUiEvent.DeleteDirectory(directory.id))
                },
                onRenameDirectory = { directory, newName ->
                    viewModel.onEvent(NotesUiEvent.RenameDirectory(directory.id, newName))
                },
                onDirectoryClick = { directory ->
                    viewModel.onEvent(NotesUiEvent.OpenDirectory(directory))
                },
            )
        }

        is NotesUiScreen.DirectoryNotes -> {
            notesListScreen(
                directoryName = screen.directory.name,
                notes = state.notes,
                directories = state.directories.filter { it.id != "all" },
                actions =
                    NotesListActions(
                        onBack = { viewModel.onEvent(NotesUiEvent.BackToDirectories) },
                        onAddNoteClick = { viewModel.onEvent(NotesUiEvent.CreateNote) },
                        onNoteDelete = { note -> viewModel.onEvent(NotesUiEvent.DeleteNote(note.id)) },
                        onNoteMove = { noteId, directoryId ->
                            viewModel.onEvent(
                                NotesUiEvent.MoveNoteToDirectory(
                                    noteId = noteId,
                                    targetDirectoryId = directoryId,
                                ),
                            )
                        },
                        onNoteClick = { note ->
                            viewModel.onEvent(NotesUiEvent.OpenNote(note))
                        },
                    ),
            )
        }

        is NotesUiScreen.NoteEditor -> {
            editorScreen(
                directoryName = screen.directory.name,
                note = screen.note,
                onBack = { viewModel.onEvent(NotesUiEvent.BackToDirectoryNotes) },
                onSave = { updated ->
                    viewModel.onEvent(NotesUiEvent.SaveNote(updated))
                },
            )
        }
    }
}
