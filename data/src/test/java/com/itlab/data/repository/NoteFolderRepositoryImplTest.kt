package com.itlab.data.repository

import com.itlab.data.dao.FolderDao
import com.itlab.data.entity.FolderEntity
import com.itlab.data.mapper.NoteFolderMapper
import com.itlab.domain.model.NoteFolder
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Clock

class NoteFolderRepositoryImplTest {
    private val folderDao = mockk<FolderDao>(relaxed = true)
    private val mapper = NoteFolderMapper()
    private val repository = NoteFolderRepositoryImpl(folderDao, mapper)

    private val testFolder =
        NoteFolder(
            id = "1",
            name = "Work",
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            metadata = emptyMap(),
        )

    @Test
    fun `observeFolders emits mapped domain list`() =
        runTest {
            val entity = mapper.toEntity(testFolder)
            coEvery { folderDao.getAllFolders() } returns flowOf(listOf(entity))

            val result = repository.observeFolders().first()

            assertEquals(1, result.size)
            assertEquals(testFolder.name, result[0].name)
        }

    @Test
    fun `renameFolder calls dao updateName`() =
        runTest {
            repository.renameFolder("1", "New Name")
            coEvery { folderDao.updateName("1", "New Name") }
        }

    @Test
    fun `getFolderById returns mapped folder or null`() =
        runTest {
            coEvery { folderDao.getFolderById("1") } returns mapper.toEntity(testFolder)
            coEvery { folderDao.getFolderById("2") } returns null

            assertEquals("Work", repository.getFolderById("1")?.name)
            assertEquals(null, repository.getFolderById("2"))
        }

    @Test
    fun `observeFolders emits empty list when dao is empty`() =
        runTest {
            coEvery { folderDao.getAllFolders() } returns flowOf(emptyList())

            val result = repository.observeFolders().first()

            assertTrue(result.isEmpty())
        }

    @Test
    fun `deleteFolder does nothing if folder not found`() =
        runTest {
            coEvery { folderDao.getFolderById("999") } returns null

            repository.deleteFolder("999")

            coVerify(exactly = 0) { folderDao.delete(any()) }
        }

    @Test
    fun `updateFolder calls dao update`() =
        runTest {
            repository.updateFolder(testFolder)
            coVerify { folderDao.update(any()) }
        }

    @Test
    fun `deleteFolder should skip deletion if folder does not exist`() =
        runTest {
            coEvery { folderDao.getFolderById("id") } returns null

            repository.deleteFolder("id")

            coVerify(exactly = 0) { folderDao.delete(any()) }
        }

    @Test
    fun `getFolderById returns null when dao returns null`() =
        runTest {
            coEvery { folderDao.getFolderById("any") } returns null
            val result = repository.getFolderById("any")
            assertNull(result)
        }

    @Test
    fun `deleteFolder handles missing folder`() =
        runTest {
            coEvery { folderDao.getFolderById("id") } returns null

            repository.deleteFolder("id")

            coVerify(exactly = 0) { folderDao.delete(any()) }
        }

    @Test
    fun `renameFolder calls dao update`() =
        runTest {
            repository.renameFolder("folder_1", "New Name")
            coVerify { folderDao.updateName("folder_1", "New Name") }
        }

    @Test
    fun `createFolder inserts entity and returns correct id`() =
        runTest {
            val folder = NoteFolder(id = "folder_777", name = "Test Folder")

            coEvery { folderDao.insert(any()) } just Runs

            val resultId = repository.createFolder(folder)

            coVerify { folderDao.insert(any()) }
            assertEquals("folder_777", resultId)
        }

    @Test
    fun `deleteFolder calls dao delete when folder is found`() =
        runTest {
            val folderId = "target_id"
            val entity = mockk<FolderEntity>(relaxed = true)

            // Возвращаем реальный объект, чтобы зайти внутрь ?.let
            coEvery { folderDao.getFolderById(folderId) } returns entity
            coEvery { folderDao.delete(entity) } just Runs

            repository.deleteFolder(folderId)

            // Эта проверка гасит красную строку `folderDao.delete(it)`
            coVerify(exactly = 1) { folderDao.delete(entity) }
        }
}
