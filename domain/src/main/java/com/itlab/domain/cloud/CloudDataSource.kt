package com.itlab.domain.cloud

import java.io.File
import kotlin.time.Instant

sealed interface Result<out T> {
    data class Success<out T>(
        val data: T,
    ) : Result<T>

    data class Error(
        val exception: Throwable,
    ) : Result<Nothing>
}

interface CloudDataSource {
    suspend fun listNoteMetadata(userId: String): Result<List<CloudNoteMetadata>>

    suspend fun listMediaMetadata(userId: String): Result<List<CloudMediaMetadata>>

    suspend fun downloadNote(key: String): Result<String>

    suspend fun uploadNote(
        key: String,
        json: String,
    ): Result<Unit>

    suspend fun deleteNote(key: String): Result<Unit>

    suspend fun uploadMedia(
        key: String,
        file: File,
        mimeType: String,
    ): Result<Unit>

    suspend fun downloadMedia(
        key: String,
        destination: File,
    ): Result<Unit>

    suspend fun deleteMedia(key: String): Result<Unit>
}

data class CloudNoteMetadata(
    val key: String,
    val updatedAt: Instant,
)

data class CloudMediaMetadata(
    val key: String,
    val mediaId: String,
    val mimeType: String,
)
