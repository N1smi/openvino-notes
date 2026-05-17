package com.itlab.data.cloud

import com.itlab.data.dao.MediaDao
import com.itlab.data.dao.NoteDao
import com.itlab.data.entity.MediaEntity
import com.itlab.data.mapper.NoteEntityJsonConverter
import com.itlab.domain.cloud.CloudDataSource
import com.itlab.domain.cloud.CloudMediaMetadata
import com.itlab.domain.cloud.Result
import com.itlab.domain.cloud.SyncManager
import com.itlab.domain.cloud.SyncState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import timber.log.Timber
import java.io.File
import java.io.IOException

class SyncManagerImpl(
    private val context: android.content.Context,
    private val noteDao: NoteDao,
    private val mediaDao: MediaDao,
    private val cloudDataSource: CloudDataSource,
    private val jsonConverter: NoteEntityJsonConverter,
) : SyncManager {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    override val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    override suspend fun sync(userId: String) {
        _syncState.value = SyncState.Syncing

        try {
            pushChanges(userId)
            pullUpdates(userId)

            _syncState.value = SyncState.Success
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            handleError("Network error during sync", e)
            throw e
        } catch (e: SerializationException) {
            handleError("Data parsing error", e)
            throw e
        } catch (e: IllegalStateException) {
            handleError("Invalid state during sync", e)
            throw e
        }
    }

    private fun handleError(
        message: String,
        e: Exception,
    ) {
        Timber.e(e, message)
        _syncState.value = SyncState.Error(e.message ?: "Unknown error")
    }

    override suspend fun pushChanges(userId: String) {
        val unsyncedEntities = noteDao.getUnsyncedNotes()
        val unsyncedMedia = mediaDao.getUnsyncedMedia()

        for (entity in unsyncedEntities) {
            val json = with(jsonConverter) { entity.toJson() }

            val result = cloudDataSource.uploadNote("users/$userId/notes/${entity.id}", json)

            when (result) {
                is Result.Success -> {
                    val syncedEntity = entity.copy(isSynced = true)
                    noteDao.update(syncedEntity)
                }
                is Result.Error -> {
                    Timber.e(result.exception, "Couldn't upload the note ${entity.id}")
                    throw result.exception
                }
            }
        }

        for (media in unsyncedMedia) {
            val path = media.localPath ?: continue

            val file = File(path)

            if (file.exists()) {
                val result =
                    cloudDataSource.uploadMedia(
                        key = "users/$userId/media/${media.noteId}_${media.id}",
                        file = file,
                        mimeType = media.mimeType,
                    )
                if (result is Result.Success) {
                    mediaDao.update(
                        media.copy(isSynced = true, remoteUrl = "users/$userId/media/${media.noteId}_${media.id}"),
                    )
                }
                if (result is Result.Error) {
                    Timber.e(result.exception, "Couldn't upload the media ${media.id}")
                    throw result.exception
                }
            }
        }
    }

    override suspend fun pullUpdates(userId: String) {
        pullNotes(userId)

        pullMedia(userId)
    }

    private suspend fun pullNotes(userId: String) {
        val metadataResult = cloudDataSource.listNoteMetadata(userId)
        val remoteMetadata =
            when (metadataResult) {
                is Result.Success -> metadataResult.data
                is Result.Error -> throw metadataResult.exception
            }

        val localNotes = noteDao.getAllNotes().first()
        val localIds = localNotes.map { it.id }

        val toDownload = remoteMetadata.filter { it.key !in localIds }

        for (meta in toDownload) {
            val downloadResult = cloudDataSource.downloadNote(meta.key)
            if (downloadResult is Result.Success) {
                val entity =
                    jsonConverter.toEntity(
                        jsonString = downloadResult.data,
                        userId = userId,
                    )
                noteDao.insert(entity)
            } else if (downloadResult is Result.Error) {
                Timber.e(downloadResult.exception, "Couldn't download note ${meta.key}")
                throw downloadResult.exception
            }
        }
    }

    private suspend fun pullMedia(userId: String) {
        val mediaMetadataResult = cloudDataSource.listMediaMetadata(userId)
        if (mediaMetadataResult is Result.Error) throw mediaMetadataResult.exception

        if (mediaMetadataResult is Result.Success) {
            val remoteMedia = mediaMetadataResult.data
            val localMedia = mediaDao.getAllMedia().first()
            val localMediaIds = localMedia.map { it.id }

            val toDownload =
                remoteMedia.filter { meta ->
                    val actualId = meta.mediaId.substringAfter("_")
                    actualId !in localMediaIds
                }

            for (mediaMeta in toDownload) {
                processMediaDownload(mediaMeta)
            }
        }
    }

    private suspend fun processMediaDownload(mediaMeta: CloudMediaMetadata) {
        val noteIdFromCloud = mediaMeta.mediaId.substringBefore("_")
        val actualMediaId = mediaMeta.mediaId.substringAfter("_")

        val destination = File(context.filesDir, "media/$actualMediaId")
        destination.parentFile?.mkdirs()

        val downloadResult = cloudDataSource.downloadMedia(mediaMeta.key, destination)

        if (downloadResult is Result.Success) {
            val cloudMimeType = mediaMeta.mimeType
            mediaDao.insert(
                MediaEntity(
                    id = actualMediaId,
                    noteId = noteIdFromCloud,
                    localPath = destination.absolutePath,
                    isSynced = true,
                    remoteUrl = mediaMeta.key,
                    mimeType = cloudMimeType,
                    type = if (cloudMimeType.startsWith("image/")) "IMAGE" else "FILE",
                ),
            )
        }
    }
}
