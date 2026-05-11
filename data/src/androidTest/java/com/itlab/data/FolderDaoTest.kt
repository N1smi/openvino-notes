package com.itlab.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.itlab.data.dao.FolderDao
import com.itlab.data.db.AppDatabase
import com.itlab.data.entity.FolderEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock

@RunWith(AndroidJUnit4::class)
class FolderDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: FolderDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.folderDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun writeFolderAndReadInList() =
        runTest {
            val folder =
                FolderEntity(
                    id = "folder_1",
                    name = "Work",
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now(),
                    metadata = mapOf("tag" to "urgent", "icon_id" to "123"),
                )

            dao.insert(folder)
            val result = dao.getFolderById("folder_1")

            assertEquals(folder.name, result?.name)
            assertEquals("urgent", result?.metadata?.get("tag"))
        }

    @Test
    fun writeFolderWithEmptyMetadata() =
        runTest {
            val folder =
                FolderEntity(
                    id = "folder_empty",
                    name = "Empty Meta",
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now(),
                    metadata = emptyMap(),
                )

            dao.insert(folder)
            val result = dao.getFolderById("folder_empty")

            assertTrue(result?.metadata?.isEmpty() == true)
        }
}
