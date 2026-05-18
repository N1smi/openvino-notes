package com.itlab.notes.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itlab.domain.model.ContentItem
import com.itlab.domain.model.Note
import com.itlab.domain.model.NoteFolder
import com.itlab.notes.media.withoutTextItems
import com.itlab.notes.ui.notes.ALL_DIRECTORY_ID
import com.itlab.notes.ui.notes.DirectoryItemUi
import com.itlab.notes.ui.notes.FAVORITES_DIRECTORY_ID
import com.itlab.notes.ui.notes.NoteItemUi
import com.itlab.notes.ui.notes.RECENT_DIRECTORY_ID
import com.itlab.notes.ui.notes.canCreateNotesInDirectory
import com.itlab.notes.ui.notes.coerceDirectoryNameLength
import com.itlab.notes.ui.notes.isVirtualDirectory
import com.itlab.notes.ui.toSingleLineText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class NotesViewModel(
    private val useCases: NotesUseCases,
) : ViewModel(),
    NotesViewModelContract {
    override var uiState: NotesUiState by mutableStateOf(
        NotesUiState(screen = NotesUiScreen.Directories),
    )
        private set
    private var notesJob: Job? = null
    private var latestFolders: List<NoteFolder> = emptyList()
    private var latestNotes: List<Note> = emptyList()

    init {
        viewModelScope.launch {
            useCases.observeFoldersUseCase().collect { folders ->
                latestFolders = folders
                recomputeDirectories()
            }
        }

        viewModelScope.launch {
            useCases.observeNotesByFolderUseCase(null).collect { notes ->
                latestNotes = notes
                recomputeDirectories()
            }
        }
    }

    override fun onEvent(event: NotesUiEvent) {
        when (event) {
            is NotesUiEvent.OpenDirectory -> openDirectory(event.directory)
            NotesUiEvent.BackToDirectories -> backToDirectories()
            is NotesUiEvent.OpenNote -> openNote(event.note)
            NotesUiEvent.CreateNote -> createNote()
            is NotesUiEvent.CreateDirectory -> {
                val normalized =
                    event.name
                        .toSingleLineText()
                        .trim()
                        .coerceDirectoryNameLength()
                if (normalized.isNotBlank()) {
                    viewModelScope.launch {
                        useCases.createFolderUseCase(
                            NoteFolder(useCases.getUserIdUseCase() ?: "local_user", name = normalized),
                        )
                    }
                }
            }
            is NotesUiEvent.RenameDirectory -> renameDirectory(event)
            is NotesUiEvent.DeleteDirectory -> deleteDirectory(event.directoryId)
            is NotesUiEvent.MoveNoteToDirectory -> {
                if (isVirtualDirectory(event.targetDirectoryId)) return
                viewModelScope.launch {
                    useCases.moveNoteToFolderUseCase(
                        folderId = event.targetDirectoryId,
                        noteId = event.noteId,
                    )
                }
            }
            is NotesUiEvent.ToggleNoteFavorite -> toggleNoteFavorite(event.noteId)
            NotesUiEvent.BackToDirectoryNotes -> backToDirectoryNotes()
            is NotesUiEvent.LeaveEditor -> leaveEditor(event.note)
            is NotesUiEvent.SaveNote -> saveNote(event.note)
            is NotesUiEvent.PersistNote -> persistNote(event.note)
            is NotesUiEvent.DeleteNote -> {
                viewModelScope.launch {
                    useCases.deleteNoteUseCase(event.noteId)
                }
            }
            is NotesUiEvent.NotesSearchQueryChanged -> onNotesSearchQueryChanged(event.query)
            is NotesUiEvent.DirectorySearchQueryChanged -> {
                uiState = uiState.copy(directorySearchQuery = event.query)
            }
        }
    }

    private fun onNotesSearchQueryChanged(query: String) {
        val directory = (uiState.screen as? NotesUiScreen.DirectoryNotes)?.directory ?: return
        uiState = uiState.copy(notesSearchQuery = query)
        startNotesCollection(directory, query)
    }

    private fun renameDirectory(event: NotesUiEvent.RenameDirectory) {
        val normalized =
            event.newName
                .toSingleLineText()
                .trim()
                .coerceDirectoryNameLength()
        if (normalized.isBlank() || isVirtualDirectory(event.directoryId)) return
        viewModelScope.launch {
            val existingFolder = useCases.getFolderUseCase(event.directoryId) ?: return@launch
            useCases.updateFolderUseCase(existingFolder.copy(name = normalized))
        }
    }

    private fun deleteDirectory(directoryId: String) {
        if (isVirtualDirectory(directoryId)) return
        viewModelScope.launch {
            useCases.deleteFolderUseCase(directoryId)
            if ((uiState.screen as? NotesUiScreen.DirectoryNotes)?.directory?.id == directoryId) {
                backToDirectories()
            }
        }
    }

    private fun openDirectory(directory: DirectoryItemUi) {
        uiState =
            uiState.copy(
                screen = NotesUiScreen.DirectoryNotes(directory = directory),
                notes = emptyList(),
                notesSearchQuery = "",
            )
        startNotesCollection(directory, searchQuery = "")
    }

    private fun startNotesCollection(
        directory: DirectoryItemUi,
        searchQuery: String,
    ) {
        notesJob?.cancel()
        notesJob =
            viewModelScope.launch {
                notesFlow(directory, searchQuery).collect { notes ->
                    val opened = uiState.screen as? NotesUiScreen.DirectoryNotes ?: return@collect
                    uiState =
                        uiState.copy(
                            notes = notes.map { it.toUi() },
                            notesSearchQuery = searchQuery,
                            screen =
                                NotesUiScreen.DirectoryNotes(
                                    directory = opened.directory.copy(noteCount = notes.size),
                                ),
                        )
                }
            }
    }

    private fun notesFlow(
        directory: DirectoryItemUi,
        searchQuery: String,
    ): Flow<List<Note>> {
        val normalizedQuery = searchQuery.trim()
        return if (normalizedQuery.isBlank()) {
            when (directory.id) {
                ALL_DIRECTORY_ID -> useCases.observeNotesUseCase()
                FAVORITES_DIRECTORY_ID -> useCases.getAllFavoritesUseCase()
                RECENT_DIRECTORY_ID ->
                    useCases.observeNotesUseCase().map { notes ->
                        notes.sortedByDescending { it.updatedAt }
                    }
                else -> useCases.observeNotesByFolderUseCase(directory.id)
            }
        } else {
            val searchFlow =
                useCases.searchNotesUseCase(
                    query = normalizedQuery,
                    folderId = directory.folderIdForSearch(),
                )
            when (directory.id) {
                FAVORITES_DIRECTORY_ID ->
                    searchFlow.map { notes -> notes.filter { it.isFavorite } }
                RECENT_DIRECTORY_ID ->
                    searchFlow.map { notes ->
                        notes.sortedByDescending { it.updatedAt }
                    }
                else -> searchFlow
            }
        }
    }

    private val backToDirectories: () -> Unit = {
        uiState =
            uiState.copy(
                screen = NotesUiScreen.Directories,
                notes = emptyList(),
                notesSearchQuery = "",
            )
    }

    private fun openNote(note: NoteItemUi) {
        val dir = (uiState.screen as? NotesUiScreen.DirectoryNotes)?.directory ?: return
        notesJob?.cancel()
        uiState =
            uiState.copy(
                screen = NotesUiScreen.NoteEditor(directory = dir, note = note),
            )
    }

    private fun createNote() {
        val dir = (uiState.screen as? NotesUiScreen.DirectoryNotes)?.directory ?: return
        if (!canCreateNotesInDirectory(dir.id)) return
        notesJob?.cancel()
        val userId = useCases.getUserIdUseCase() ?: "local_user"
        val newNote = Note(userId = userId, folderId = dir.id.asDomainFolderId()).toUi()
        uiState =
            uiState.copy(
                screen = NotesUiScreen.NoteEditor(directory = dir, note = newNote),
            )
    }

    private fun backToDirectoryNotes() {
        val editor = uiState.screen as? NotesUiScreen.NoteEditor ?: return
        val directory = editor.directory
        uiState = uiState.copy(screen = NotesUiScreen.DirectoryNotes(directory = directory))
        startNotesCollection(directory, uiState.notesSearchQuery)
    }

    private fun toggleNoteFavorite(noteId: String) {
        viewModelScope.launch {
            useCases.switchFavoriteUseCase(noteId)
            val editor = uiState.screen as? NotesUiScreen.NoteEditor
            if (editor?.note?.id == noteId) {
                uiState =
                    uiState.copy(
                        screen =
                            editor.copy(
                                note = editor.note.copy(isFavorite = !editor.note.isFavorite),
                            ),
                    )
            }
        }
    }

    private fun persistNote(note: NoteItemUi) {
        val editor = uiState.screen as? NotesUiScreen.NoteEditor ?: return
        viewModelScope.launch {
            persistNoteToRepository(note, editor.directory)
        }
    }

    private fun leaveEditor(note: NoteItemUi) {
        val editor = uiState.screen as? NotesUiScreen.NoteEditor ?: return
        viewModelScope.launch {
            if (note.title.trim().isNotEmpty()) {
                persistNoteToRepository(note, editor.directory)
            }
            navigateBackToDirectoryNotes(editor.directory)
        }
    }

    private fun saveNote(note: NoteItemUi) {
        val editor = uiState.screen as? NotesUiScreen.NoteEditor ?: return
        viewModelScope.launch {
            if (!persistNoteToRepository(note, editor.directory)) return@launch
            navigateBackToDirectoryNotes(editor.directory)
        }
    }

    private fun navigateBackToDirectoryNotes(directory: DirectoryItemUi) {
        uiState = uiState.copy(screen = NotesUiScreen.DirectoryNotes(directory = directory))
        startNotesCollection(directory, uiState.notesSearchQuery)
    }

    private suspend fun persistNoteToRepository(
        note: NoteItemUi,
        directory: DirectoryItemUi,
    ): Boolean {
        if (note.title.trim().isEmpty()) return false
        val targetFolderId = note.folderId ?: directory.id.asDomainFolderId()
        val existing = useCases.getNoteUseCase(note.id)
        val result =
            if (existing != null) {
                useCases.updateNoteUseCase(existing.applyUiUpdate(note, targetFolderId))
            } else {
                useCases.createNoteUseCase(note.toDomain(folderId = targetFolderId))
            }
        if (result.isFailure) return false
        val editor = uiState.screen as? NotesUiScreen.NoteEditor
        if (editor?.note?.id == note.id) {
            uiState = uiState.copy(screen = editor.copy(note = note))
        }
        return true
    }

    private fun recomputeDirectories() {
        val countsByFolderId = latestNotes.groupingBy { it.folderId }.eachCount()
        val allNotesCount = latestNotes.size

        val favoritesCount = latestNotes.count { it.isFavorite }
        val allNotesDir = DirectoryItemUi(id = ALL_DIRECTORY_ID, name = "All Notes", noteCount = allNotesCount)
        val favoritesDir =
            DirectoryItemUi(
                id = FAVORITES_DIRECTORY_ID,
                name = "Favorites",
                noteCount = favoritesCount,
            )

        val directories =
            listOf(allNotesDir, favoritesDir) +
                latestFolders.map { folder ->
                    val count = countsByFolderId[folder.id] ?: 0
                    folder.toUi(noteCount = count)
                }

        uiState = uiState.copy(directories = directories)

        // If a directory screen is currently open, keep the directory object in sync with the new count.
        val opened = uiState.screen as? NotesUiScreen.DirectoryNotes
        if (opened != null) {
            val updatedDir = directories.firstOrNull { it.id == opened.directory.id }
            if (updatedDir != null && updatedDir.noteCount != opened.directory.noteCount) {
                uiState = uiState.copy(screen = NotesUiScreen.DirectoryNotes(directory = updatedDir))
            }
        }
    }

    override fun onCleared() {
        notesJob?.cancel()
        super.onCleared()
    }
}

internal fun NoteFolder.toUi(noteCount: Int): DirectoryItemUi =
    DirectoryItemUi(id = id, name = name, noteCount = noteCount)

internal fun Note.toUi(): NoteItemUi =
    NoteItemUi(
        id = id,
        userId = userId,
        title = title,
        content =
            contentItems
                .filterIsInstance<ContentItem.Text>()
                .joinToString("\n") { it.text },
        folderId = folderId,
        attachments = contentItems.withoutTextItems(),
        isFavorite = isFavorite,
    )

internal fun NoteItemUi.toContentItems(): List<ContentItem> =
    buildList {
        if (content.isNotBlank()) add(ContentItem.Text(text = content))
        addAll(attachments.withoutTextItems())
    }

internal fun NoteItemUi.toDomain(folderId: String?): Note =
    Note(
        userId = userId,
        id = id,
        title = title,
        folderId = folderId,
        contentItems = toContentItems(),
        isFavorite = isFavorite,
    )

internal fun Note.applyUiUpdate(
    ui: NoteItemUi,
    targetFolderId: String?,
): Note =
    copy(
        title = ui.title,
        folderId = targetFolderId,
        contentItems = ui.toContentItems(),
        isFavorite = ui.isFavorite,
    )

internal fun String.asDomainFolderId(): String? =
    when (this) {
        ALL_DIRECTORY_ID,
        RECENT_DIRECTORY_ID,
        FAVORITES_DIRECTORY_ID,
        -> null
        else -> this
    }

internal fun DirectoryItemUi.folderIdForSearch(): String? =
    when (id) {
        ALL_DIRECTORY_ID,
        RECENT_DIRECTORY_ID,
        FAVORITES_DIRECTORY_ID,
        -> null
        else -> id
    }

internal fun filterDirectoriesByName(
    directories: List<DirectoryItemUi>,
    query: String,
): List<DirectoryItemUi> {
    val normalized = query.trim()
    if (normalized.isBlank()) return directories
    return directories.filter { directory ->
        directory.name.contains(normalized, ignoreCase = true)
    }
}
