package com.itlab.domain

import com.itlab.domain.model.Note
import com.itlab.domain.model.NoteFolder
import com.itlab.domain.repository.NoteFolderRepository
import com.itlab.domain.repository.NotesRepository
import com.itlab.domain.usecase.noteusecase.AddTagUseCase
import com.itlab.domain.usecase.noteusecase.CreateNoteUseCase
import com.itlab.domain.usecase.noteusecase.DeleteNoteUseCase
import com.itlab.domain.usecase.noteusecase.DeleteTagUseCase
import com.itlab.domain.usecase.noteusecase.DuplicateNoteUseCase
import com.itlab.domain.usecase.noteusecase.GetAllFavoritesUseCase
import com.itlab.domain.usecase.noteusecase.GetNoteUseCase
import com.itlab.domain.usecase.noteusecase.MoveNoteToFolderUseCase
import com.itlab.domain.usecase.noteusecase.ObserveNotesUseCase
import com.itlab.domain.usecase.noteusecase.SwitchFavoriteUseCase
import com.itlab.domain.usecase.noteusecase.UpdateNoteUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class NoteUseCasesTest {
    private class FakeNotesRepo : NotesRepository {
        private val store = mutableMapOf<String, Note>()
        private val flow = MutableStateFlow<List<Note>>(emptyList())

        override fun observeNotes() = flow

        override fun observeNotesByFolder(folderId: String) = flow

        override suspend fun getNoteById(id: String): Note? = store[id]

        override suspend fun createNote(note: Note): String {
            store[note.id] = note
            flow.value = store.values.toList()
            return note.id
        }

        override suspend fun updateNote(note: Note) {
            store[note.id] = note
            flow.value = store.values.toList()
        }

        override suspend fun deleteNote(id: String) {
            store.remove(id)
            flow.value = store.values.toList()
        }
    }

    private class FakeFolderRepo : NoteFolderRepository {
        private val store = mutableMapOf<String, NoteFolder>()

        override fun observeFolders() = MutableStateFlow(emptyList<NoteFolder>())

        override suspend fun createFolder(folder: NoteFolder): String {
            store[folder.id] = folder
            return folder.id
        }

        override suspend fun renameFolder(
            id: String,
            name: String,
        ) = Unit

        override suspend fun deleteFolder(id: String) = Unit

        override suspend fun getFolderById(id: String): NoteFolder? = store[id]

        override suspend fun updateFolder(folder: NoteFolder) = Unit
    }

    @Test
    fun create_update_delete_note() =
        runBlocking {
            val repo = FakeNotesRepo()

            val create = CreateNoteUseCase(repo)
            val update = UpdateNoteUseCase(repo)
            val delete = DeleteNoteUseCase(repo)
            val get = GetNoteUseCase(repo)

            val note = Note(title = "A")

            val id = create(note)

            val created = get(id)!!

            val updated = created.copy(title = "B")
            update(updated)

            val result = get(id)
            assertEquals("B", result?.title)

            delete(id)

            val result2 = get(id)
            assertNull(result2)
        }

    @Test
    fun moveNoteToFolder_works() =
        runBlocking {
            val notesRepo = FakeNotesRepo()
            val folderRepo = FakeFolderRepo()

            val move = MoveNoteToFolderUseCase(notesRepo)
            val createNote = CreateNoteUseCase(notesRepo)

            val folder = NoteFolder(id = "f1", name = "Folder")
            folderRepo.createFolder(folder)

            val note = Note(title = "Note")

            val noteId = createNote(note)

            move("f1", noteId)

            val updated = notesRepo.getNoteById(noteId)

            assertEquals("f1", updated?.folderId)
        }

    @Test
    fun observeNotes_returnsData() =
        runBlocking {
            val repo = FakeNotesRepo()
            val observe = ObserveNotesUseCase(repo)
            val create = CreateNoteUseCase(repo)

            create(Note(title = "Test"))
            val list = observe().first()

            assertEquals(1, list.size)
        }

    @Test
    fun addTag_addsTagToNote() =
        runBlocking {
            val repo = FakeNotesRepo()
            val useCase = AddTagUseCase(repo)

            val note =
                Note(
                    id = "n1",
                    title = "Test",
                    tags = setOf("old"),
                )
            repo.createNote(note)

            useCase("n1", "new-tag")

            val updated = repo.getNoteById("n1")

            assertEquals(setOf("old", "new-tag"), updated?.tags)
        }

    @Test
    fun addTag_throwsIfNoteNotFound() =
        runBlocking {
            val repo = FakeNotesRepo()
            val useCase = AddTagUseCase(repo)

            try {
                useCase("missing_id", "tag")
                fail("Expected IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertEquals("Note not found: missing_id", e.message)
            }
        }

    @Test
    fun deleteTag_removesTagFromNote() =
        runBlocking {
            val repo = FakeNotesRepo()
            val useCase = DeleteTagUseCase(repo)

            val note =
                Note(
                    id = "n2",
                    title = "Test",
                    tags = setOf("old", "remove-me"),
                )
            repo.createNote(note)

            useCase("n2", "remove-me")

            val updated = repo.getNoteById("n2")

            assertEquals(setOf("old"), updated?.tags)
        }

    @Test
    fun deleteTag_throwsIfNoteNotFound() =
        runBlocking {
            val repo = FakeNotesRepo()
            val useCase = DeleteTagUseCase(repo)

            try {
                useCase("missing_id", "tag")
                fail("Expected IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertEquals("Note not found: missing_id", e.message)
            }
        }

    @Test
    fun duplicateNote_createsCopyWithNewIdAndCopiedTitle() =
        runBlocking {
            val repo = FakeNotesRepo()
            val useCase = DuplicateNoteUseCase(repo)

            val original =
                Note(
                    id = "n3",
                    title = "Hello",
                    tags = setOf("kotlin"),
                    isFavorite = true,
                    summary = "summary",
                )
            repo.createNote(original)

            val newId = useCase("n3")

            val duplicated = repo.getNoteById(newId)

            assertEquals(true, duplicated != null)
            assertEquals("Hello Copy", duplicated?.title)
            assertEquals(setOf("kotlin"), duplicated?.tags)
            assertEquals(true, duplicated?.isFavorite)
            assertEquals("summary", duplicated?.summary)
            assertEquals("n3", original.id)
            assertEquals(false, original.id == newId)
        }

    @Test
    fun duplicateNote_usesCopyWhenTitleBlank() =
        runBlocking {
            val repo = FakeNotesRepo()
            val useCase = DuplicateNoteUseCase(repo)

            val original =
                Note(
                    id = "n4",
                    title = "   ",
                )
            repo.createNote(original)

            val newId = useCase("n4")

            val duplicated = repo.getNoteById(newId)

            assertEquals("Copy", duplicated?.title)
        }

    @Test
    fun duplicateNote_throwsIfNoteNotFound() =
        runBlocking {
            val repo = FakeNotesRepo()
            val useCase = DuplicateNoteUseCase(repo)

            try {
                useCase("missing_id")
                fail("Expected IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertEquals("Note not found: missing_id", e.message)
            }
        }

    @Test
    fun getAllFavorites_returnsOnlyFavoriteNotes() =
        runBlocking {
            val repo = FakeNotesRepo()
            val useCase = GetAllFavoritesUseCase(repo)

            repo.createNote(
                Note(
                    id = "n5",
                    title = "A",
                    isFavorite = true,
                ),
            )
            repo.createNote(
                Note(
                    id = "n6",
                    title = "B",
                    isFavorite = false,
                ),
            )

            val list = useCase().first()

            assertEquals(1, list.size)
            assertEquals("n5", list.first().id)
        }

    @Test
    fun switchFavorite_turnsFavoriteOnAndOff() =
        runBlocking {
            val repo = FakeNotesRepo()
            val useCase = SwitchFavoriteUseCase(repo)

            val note =
                Note(
                    id = "n7",
                    title = "Fav",
                    isFavorite = false,
                )
            repo.createNote(note)

            useCase("n7")
            val afterFirstSwitch = repo.getNoteById("n7")
            assertEquals(true, afterFirstSwitch?.isFavorite)

            useCase("n7")
            val afterSecondSwitch = repo.getNoteById("n7")
            assertEquals(false, afterSecondSwitch?.isFavorite)
        }

    @Test
    fun switchFavorite_throwsIfNoteNotFound() =
        runBlocking {
            val repo = FakeNotesRepo()
            val useCase = SwitchFavoriteUseCase(repo)

            try {
                useCase("missing_id")
                fail("Expected IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                assertEquals("Note not found", e.message)
            }
        }
}
