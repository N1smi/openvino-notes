package com.itlab.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.itlab.data.entity.MediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media")
    fun getAllMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE noteId = :noteId")
    fun getMediaForNoteFlow(noteId: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE noteId = :noteId")
    suspend fun getMediaForNote(noteId: String): List<MediaEntity>

    @Query("SELECT * FROM media WHERE isSynced = 0")
    suspend fun getUnsyncedMedia(): List<MediaEntity>

    @Query("DELETE FROM media WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: String)

    @Query("DELETE FROM media")
    suspend fun deleteAll()

    @Insert
    suspend fun insert(media: MediaEntity)

    @Update
    suspend fun update(media: MediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mediaList: List<MediaEntity>)

    @Delete
    suspend fun delete(media: MediaEntity)
}
