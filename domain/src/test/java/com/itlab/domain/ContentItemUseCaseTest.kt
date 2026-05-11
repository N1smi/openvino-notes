package com.itlab.domain

import com.itlab.domain.model.ContentItem
import com.itlab.domain.model.Note
import com.itlab.domain.repository.NotesRepository
import com.itlab.domain.usecase.contentusecase.AddContentItemUseCase
import com.itlab.domain.usecase.contentusecase.CreateContentItemUseCase
import com.itlab.domain.usecase.contentusecase.DeleteContentItemUseCase
import com.itlab.domain.usecase.contentusecase.GetContentItemUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentItemUseCaseTest {
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

    @Test
    fun add_get_delete_content_item_flow() =
        runBlocking {
            val repo = FakeNotesRepo()

            val createItem = CreateContentItemUseCase()
            val addItem = AddContentItemUseCase(repo)
            val getItem = GetContentItemUseCase(repo)
            val deleteItem = DeleteContentItemUseCase(repo)

            val note = Note(id = "n1", title = "Test")
            repo.createNote(note)

            val item =
                createItem(
                    ContentItem.Text(
                        text = "Hello",
                    ),
                )

            addItem("n1", item)

            val found = getItem("n1", item.id)
            assertEquals("Hello", (found as ContentItem.Text).text)

            deleteItem("n1", item.id)

            val afterDelete = getItem("n1", item.id)
            assertNull(afterDelete)
        }

    @Test(expected = IllegalArgumentException::class)
    fun addContentItem_without_id_throws() =
        runBlocking {
            val repo = FakeNotesRepo()
            val addItem = AddContentItemUseCase(repo)

            val note = Note(id = "n1", title = "Test")
            repo.createNote(note)

            val badItem =
                ContentItem.Text(
                    id = "",
                    text = "Hello",
                )

            addItem("n1", badItem)
        }

    @Test
    fun add_multiple_content_items() =
        runBlocking {
            val repo = FakeNotesRepo()
            val createItem = CreateContentItemUseCase()
            val addItem = AddContentItemUseCase(repo)
            val getItem = GetContentItemUseCase(repo)

            val note = Note(id = "n1", title = "Test")
            repo.createNote(note)

            val item1 = createItem(ContentItem.Text(text = "A"))
            val item2 = createItem(ContentItem.Text(text = "B"))

            addItem("n1", item1)
            addItem("n1", item2)

            val found1 = getItem("n1", item1.id)
            val found2 = getItem("n1", item2.id)

            assertEquals("A", (found1 as ContentItem.Text).text)
            assertEquals("B", (found2 as ContentItem.Text).text)
        }

    @Test
    fun delete_non_existing_item_does_nothing() =
        runBlocking {
            val repo = FakeNotesRepo()
            val deleteItem = DeleteContentItemUseCase(repo)

            val note = Note(id = "n1", title = "Test")
            repo.createNote(note)

            deleteItem("n1", "wrong-id") // не должно упасть

            val result = repo.getNoteById("n1")
            assertEquals(0, result?.contentItems?.size)
        }

    @Test
    fun content_item_does_not_affect_original_note_instance() =
        runBlocking {
            val repo = FakeNotesRepo()
            val createItem = CreateContentItemUseCase()
            val addItem = AddContentItemUseCase(repo)

            val note = Note(id = "n1", title = "Test")
            repo.createNote(note)

            val item = createItem(ContentItem.Text(text = "Hello"))

            addItem("n1", item)

            assertEquals(0, note.contentItems.size)
        }
}
