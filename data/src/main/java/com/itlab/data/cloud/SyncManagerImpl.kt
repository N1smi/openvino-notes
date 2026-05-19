package com.itlab.data.cloud

import com.itlab.data.dao.FolderDao
import com.itlab.data.dao.MediaDao
import com.itlab.data.dao.NoteDao
import com.itlab.data.entity.MediaEntity
import com.itlab.data.mapper.FolderEntityJsonConverter
import com.itlab.data.mapper.NoteEntityJsonConverter
import com.itlab.domain.cloud.CloudDataSource
import com.itlab.domain.cloud.CloudMediaMetadata
import com.itlab.domain.cloud.DomainFile
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

data class SyncDaoContainer(
    val noteDao: NoteDao,
    val folderDao: FolderDao,
    val mediaDao: MediaDao,
)

data class SyncMappers(
    val jsonConverterNote: NoteEntityJsonConverter,
    val jsonConverterFolder: FolderEntityJsonConverter,
)

class SyncManagerImpl(
    private val pusher: SyncPusher,
    private val puller: SyncPuller,
    private val cleaner: SyncCleaner,
) : SyncManager {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    override val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    override suspend fun sync(userId: String) {
        _syncState.value = SyncState.Syncing

        try {
            pusher.pushChanges(userId)

            val pullSuccessful = pullUpdatesAndClean(userId)
            _syncState.value =
                if (pullSuccessful) {
                    SyncState.Success
                } else {
                    SyncState.Error("Pull failed, but changes were pushed successfully")
                }
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

    private suspend fun pullUpdatesAndClean(userId: String): Boolean =
        try {
            pullUpdates(userId)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Timber.e(e, "Network error during pull updates, but push was successful")
            false
        } catch (e: SerializationException) {
            Timber.e(e, "Data parsing error during pull updates, but push was successful")
            false
        }

    private fun handleError(
        message: String,
        e: Exception,
    ) {
        Timber.e(e, message)
        _syncState.value = SyncState.Error(e.message ?: "Unknown error")
    }

    override suspend fun pushChanges(userId: String) = pusher.pushChanges(userId)

    override suspend fun pullUpdates(userId: String) {
        val (folders, notes, media) = puller.pullUpdates(userId)
        cleaner.cleanMissingMediaLocally(userId, media)
        cleaner.cleanMissingNotesLocally(userId, notes)
        cleaner.cleanMissingFoldersLocally(userId, folders)
    }
}

class SyncPuller(
    private val daos: SyncDaoContainer,
    private val mappers: SyncMappers,
    private val cloudDataSource: CloudDataSource,
    private val context: android.content.Context,
) {
    suspend fun pullUpdates(userId: String): Triple<Set<String>, Set<String>, Set<String>> {
        val folders = pullFolders(userId)
        val notes = pullNotes(userId)
        val media = pullMedia(userId)
        return Triple(folders, notes, media)
    }

    private suspend fun pullFolders(userId: String): Set<String> {
        val metadataResult = cloudDataSource.listFolderMetadata(userId)
        val remoteMetadata =
            when (metadataResult) {
                is Result.Success -> metadataResult.data
                is Result.Error -> throw metadataResult.exception
            }

        val remoteIds = remoteMetadata.map { it.key.substringAfterLast('/') }.toSet()

        val localFolders = daos.folderDao.getActiveFoldersByUserId(userId).first()
        val localIds = localFolders.map { it.id }.toSet()

        val toDownload =
            remoteMetadata.filter { remoteMeta ->
                val remoteFolderId = remoteMeta.key.substringAfterLast('/')
                remoteFolderId !in localIds
            }

        for (meta in toDownload) {
            val downloadResult = cloudDataSource.downloadFolder(meta.key)
            when (downloadResult) {
                is Result.Success -> {
                    val folderEntity =
                        mappers.jsonConverterFolder.toEntity(
                            jsonString = downloadResult.data,
                            userId = userId,
                        )
                    daos.folderDao.insert(folderEntity)
                }
                is Result.Error -> {
                    Timber.e(downloadResult.exception, "Couldn't download folder ${meta.key}")
                    throw downloadResult.exception
                }
            }
        }

        return remoteIds
    }

    private suspend fun pullNotes(userId: String): Set<String> {
        val metadataResult = cloudDataSource.listNoteMetadata(userId)
        val remoteMetadata =
            when (metadataResult) {
                is Result.Success -> metadataResult.data
                is Result.Error -> throw metadataResult.exception
            }

        val remoteIds = remoteMetadata.map { it.key.substringAfterLast('/') }.toSet()

        val localNotes = daos.noteDao.getAllNotesByUserId(userId).first()
        val localIds = localNotes.map { it.id }.toSet()

        val toDownload =
            remoteMetadata.filter { remoteMeta ->
                val remoteNoteId = remoteMeta.key.substringAfterLast('/')
                remoteNoteId !in localIds
            }

        for (meta in toDownload) {
            val downloadResult = cloudDataSource.downloadNote(meta.key)
            if (downloadResult is Result.Success) {
                val entity =
                    mappers.jsonConverterNote.toEntity(
                        jsonString = downloadResult.data,
                        userId = userId,
                    )
                daos.noteDao.insert(entity)
            } else if (downloadResult is Result.Error) {
                Timber.e(downloadResult.exception, "Couldn't download note ${meta.key}")
                throw downloadResult.exception
            }
        }

        return remoteIds
    }

    private suspend fun pullMedia(userId: String): Set<String> {
        val mediaMetadataResult = cloudDataSource.listMediaMetadata(userId)
        if (mediaMetadataResult is Result.Error) throw mediaMetadataResult.exception

        val remoteMedia = (mediaMetadataResult as Result.Success).data
        val remoteMediaIds = remoteMedia.map { it.mediaId.substringAfter("_") }.toSet()

        val localMedia = daos.mediaDao.getAllMediaByUserId(userId).first()
        val localMediaIds = localMedia.map { it.id }.toSet()

        val toDownload =
            remoteMedia.filter { meta ->
                val actualId = meta.mediaId.substringAfter("_")
                actualId !in localMediaIds
            }

        for (mediaMeta in toDownload) {
            processMediaDownload(mediaMeta)
        }

        return remoteMediaIds
    }

    private suspend fun processMediaDownload(mediaMeta: CloudMediaMetadata) {
        val noteIdFromCloud = mediaMeta.mediaId.substringBefore("_")
        val actualMediaId = mediaMeta.mediaId.substringAfter("_")

        val destination = File(context.filesDir, "media/$actualMediaId")
        destination.parentFile?.mkdirs()

        val downloadResult = cloudDataSource.downloadMedia(mediaMeta.key, DomainFile(destination.absolutePath))

        if (downloadResult is Result.Success) {
            val cloudMimeType = mediaMeta.mimeType
            daos.mediaDao.insert(
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

class SyncPusher(
    private val daos: SyncDaoContainer,
    private val mappers: SyncMappers,
    private val cloudDataSource: CloudDataSource,
) {
    suspend fun pushChanges(userId: String) {
        pushFolders(userId)
        pushNotes(userId)
        pushMedia(userId)
    }

    private suspend fun pushFolders(userId: String) {
        val unsyncedFolders = daos.folderDao.getUnsyncedFolders(userId)

        for (folder in unsyncedFolders) {
            val cloudKey = "users/$userId/folders/${folder.id}"

            if (folder.isDeleted) {
                val result = cloudDataSource.deleteObject(cloudKey)
                when (result) {
                    is Result.Success -> {
                        daos.folderDao.hardDeleteById(folder.id, userId)
                    }
                    is Result.Error -> {
                        Timber.e(result.exception, "Failed to delete remote folder ${folder.id}")
                        throw result.exception
                    }
                }
            } else {
                val folderJson = with(mappers.jsonConverterFolder) { folder.toJson() }

                val result = cloudDataSource.uploadFolder(cloudKey, folderJson)
                when (result) {
                    is Result.Success -> {
                        daos.folderDao.update(folder.copy(isSynced = true))
                    }
                    is Result.Error -> {
                        Timber.e(result.exception, "Failed to upload folder ${folder.id}")
                        throw result.exception
                    }
                }
            }
        }
    }

    private suspend fun pushNotes(userId: String) {
        val unsyncedNotes = daos.noteDao.getUnsyncedNotes(userId)
        for (entity in unsyncedNotes) {
            val cloudKey = "users/$userId/notes/${entity.id}"
            val json = with(mappers.jsonConverterNote) { entity.toJson() }
            val result = cloudDataSource.uploadNote(cloudKey, json)

            if (result is Result.Success) {
                daos.noteDao.update(entity.copy(isSynced = true))
            } else if (result is Result.Error) {
                Timber.e(result.exception, "Couldn't upload note ${entity.id}")
                throw result.exception
            }
        }

        val deletedNotes = daos.noteDao.getDeletedNotes(userId)
        for (entity in deletedNotes) {
            val cloudKey = "users/$userId/notes/${entity.id}"
            val result = cloudDataSource.deleteObject(cloudKey)

            if (result is Result.Success) {
                daos.noteDao.hardDeleteById(id = entity.id, userId = userId)
            } else if (result is Result.Error) {
                Timber.e(result.exception, "Failed to delete remote note ${entity.id}")
                throw result.exception
            }
        }
    }

    private suspend fun pushMedia(userId: String) {
        val unsyncedMedia = daos.mediaDao.getUnsyncedMedia(userId)
        for (media in unsyncedMedia) {
            val cloudKey = "users/$userId/media/${media.noteId}_${media.id}"
            val path = media.localPath ?: continue
            val file = File(path)

            if (file.exists()) {
                val result =
                    cloudDataSource.uploadMedia(
                        key = cloudKey,
                        file = DomainFile(file.absolutePath),
                        mimeType = media.mimeType,
                    )
                if (result is Result.Success) {
                    daos.mediaDao.update(media.copy(isSynced = true, remoteUrl = cloudKey))
                } else if (result is Result.Error) {
                    Timber.e(result.exception, "Couldn't upload media ${media.id}")
                    throw result.exception
                }
            }
        }

        val deletedMedia = daos.mediaDao.getDeletedMediaToSync(userId)
        for (media in deletedMedia) {
            val cloudKey = "users/$userId/media/${media.noteId}_${media.id}"
            val result = cloudDataSource.deleteObject(cloudKey)

            if (result is Result.Success) {
                media.localPath?.let { File(it).delete() }
                daos.mediaDao.hardDelete(media)
            } else if (result is Result.Error) {
                Timber.e(result.exception, "Failed to delete remote media ${media.id}")
                throw result.exception
            }
        }
    }
}

class SyncCleaner(
    private val folderDao: FolderDao,
    private val noteDao: NoteDao,
    private val mediaDao: MediaDao,
) {
    suspend fun cleanMissingFoldersLocally(
        userId: String,
        remoteIds: Set<String>,
    ) {
        val localIds =
            folderDao
                .getActiveFoldersByUserId(userId)
                .first()
                .map { it.id }
                .toSet()
        val toDeleteLocally = localIds - remoteIds
        for (folderId in toDeleteLocally) {
            folderDao.hardDeleteById(folderId, userId)
        }
    }

    suspend fun cleanMissingNotesLocally(
        userId: String,
        remoteIds: Set<String>,
    ) {
        val localIds =
            noteDao
                .getAllNotesByUserId(userId)
                .first()
                .map { it.id }
                .toSet()
        val toDeleteLocally = localIds - remoteIds
        for (noteId in toDeleteLocally) {
            noteDao.hardDeleteById(id = noteId, userId = userId)
        }
    }

    suspend fun cleanMissingMediaLocally(
        userId: String,
        remoteMediaIds: Set<String>,
    ) {
        val localMedia = mediaDao.getAllMediaByUserId(userId).first()
        val localMediaIds = localMedia.map { it.id }.toSet()
        val toDeleteLocally = localMediaIds - remoteMediaIds
        for (mediaId in toDeleteLocally) {
            val mediaEntity = localMedia.find { it.id == mediaId }
            mediaEntity?.localPath?.let { path -> File(path).delete() }
            mediaEntity?.let { mediaDao.hardDelete(it) }
        }
    }
}
