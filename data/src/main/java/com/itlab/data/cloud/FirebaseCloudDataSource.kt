package com.itlab.data.cloud

import com.google.firebase.storage.FirebaseStorage
import com.itlab.domain.cloud.CloudDataSource
import com.itlab.domain.cloud.CloudMediaMetadata
import com.itlab.domain.cloud.CloudNoteMetadata
import com.itlab.domain.cloud.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlin.time.Instant

class FirebaseCloudDataSource(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
) : CloudDataSource {
    private val rootRef = storage.reference

    override suspend fun listNoteMetadata(userId: String): Result<List<CloudNoteMetadata>> =
        safeCall {
            val listRef = rootRef.child("users/$userId/notes")
            val result = listRef.listAll().await()

            val metadataList =
                result.items.map { itemRef ->
                    val metadata = itemRef.metadata.await()
                    CloudNoteMetadata(
                        key = itemRef.path,
                        updatedAt = Instant.fromEpochMilliseconds(metadata.updatedTimeMillis),
                    )
                }
            metadataList
        }

    override suspend fun listMediaMetadata(userId: String): Result<List<CloudMediaMetadata>> =
        safeCall {
            val mediaRef = rootRef.child("users/$userId/media")
            val result = mediaRef.listAll().await()

            result.items.map { itemRef ->
                val metadata = itemRef.metadata.await()
                CloudMediaMetadata(
                    key = itemRef.path,
                    mediaId = itemRef.name,
                    mimeType = metadata.contentType ?: "application/octet-stream",
                )
            }
        }

    override suspend fun downloadNote(key: String): Result<String> =
        safeCall {
            val fileRef = rootRef.child(key)
            val bytes = fileRef.getBytes(MAX_NOTE_SIZE).await()
            String(bytes)
        }

    override suspend fun uploadNote(
        key: String,
        json: String,
    ): Result<Unit> =
        safeCall {
            val fileRef = rootRef.child(key)
            fileRef.putBytes(json.toByteArray()).await()
            Unit
        }

    override suspend fun deleteNote(key: String): Result<Unit> =
        safeCall {
            rootRef.child(key).delete().await()
            Unit
        }

    override suspend fun uploadMedia(
        key: String,
        file: File,
        mimeType: String,
    ): Result<Unit> =
        safeCall {
            val fileRef = rootRef.child(key)
            val metadata =
                com.google.firebase.storage.storageMetadata {
                    contentType = mimeType
                }
            file.inputStream().use { stream ->
                fileRef.putStream(stream, metadata).await()
            }
            Unit
        }

    override suspend fun downloadMedia(
        key: String,
        destination: File,
    ): Result<Unit> =
        safeCall {
            val fileRef = rootRef.child(key)
            fileRef.getFile(destination).await()
            Unit
        }

    override suspend fun deleteMedia(key: String): Result<Unit> =
        safeCall {
            rootRef.child(key).delete().await()
            Unit
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend inline fun <T> safeCall(crossinline block: suspend () -> T): Result<T> =
        try {
            Result.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: com.google.firebase.FirebaseException) {
            Result.Error(e)
        } catch (e: java.io.IOException) {
            Result.Error(e)
        } catch (e: Exception) {
            Result.Error(e)
        }

    companion object {
        private const val MAX_NOTE_SIZE = 5 * 1024 * 1024L // 5MB
    }
}
