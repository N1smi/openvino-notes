package com.itlab.domain

import com.itlab.domain.model.NoteFolder
import com.itlab.domain.repository.NoteFolderRepository
import com.itlab.domain.usecase.folderusecase.CreateFolderUseCase
import com.itlab.domain.usecase.folderusecase.DeleteFolderUseCase
import com.itlab.domain.usecase.folderusecase.GetFolderUseCase
import com.itlab.domain.usecase.folderusecase.ObserveFoldersUseCase
import com.itlab.domain.usecase.folderusecase.UpdateFolderUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderUseCasesTest {
    private class FakeFolderRepo : NoteFolderRepository {
        private val store = mutableMapOf<String, NoteFolder>()
        private val flow = MutableStateFlow<List<NoteFolder>>(emptyList())

        override fun observeFolders() = flow

        override suspend fun createFolder(folder: NoteFolder): String {
            store[folder.id] = folder
            flow.value = store.values.toList()
            return folder.id
        }

        override suspend fun renameFolder(
            id: String,
            name: String,
        ) {
            val folder = store[id] ?: return
            val updated = folder.copy(name = name)
            store[id] = updated
            flow.value = store.values.toList()
        }

        override suspend fun deleteFolder(id: String) {
            store.remove(id)
            flow.value = store.values.toList()
        }

        override suspend fun getFolderById(id: String): NoteFolder? = store[id]

        override suspend fun updateFolder(folder: NoteFolder) {
            store[folder.id] = folder
            flow.value = store.values.toList()
        }
    }

    @Test
    fun createFolder_and_getFolder() =
        runBlocking {
            val repo = FakeFolderRepo()

            val create = CreateFolderUseCase(repo)
            val get = GetFolderUseCase(repo)

            val folder = NoteFolder(name = "Test")

            val id = create(folder)

            val result = get(id)

            assertEquals("Test", result?.name)
        }

    @Test
    fun updateFolder_works() =
        runBlocking {
            val repo = FakeFolderRepo()

            val create = CreateFolderUseCase(repo)
            val update = UpdateFolderUseCase(repo)
            val get = GetFolderUseCase(repo)

            val folder = NoteFolder(name = "Old")

            val id = create(folder)

            val created = get(id)!!

            val updated = created.copy(name = "New")
            update(updated)

            val result = get(id)

            assertEquals("New", result?.name)
        }

    @Test
    fun deleteFolder_works() =
        runBlocking {
            val repo = FakeFolderRepo()

            val create = CreateFolderUseCase(repo)
            val delete = DeleteFolderUseCase(repo)
            val get = GetFolderUseCase(repo)

            val folder = NoteFolder(name = "Test")

            val id = create(folder)

            delete(id)

            val result = get(id)

            assertNull(result)
        }

    @Test
    fun observeFolders_emitsData() =
        runBlocking {
            val repo = FakeFolderRepo()
            val create = CreateFolderUseCase(repo)
            val observe = ObserveFoldersUseCase(repo)

            create(NoteFolder(name = "A"))

            val list = observe().first()

            assertEquals(1, list.size)
        }
}
