package com.itlab.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val folderId: String? = null,
    val contentItems: List<ContentItem> = emptyList(),
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
    val tags: Set<String> = emptySet(),
    val isFavorite: Boolean = false,
    val summary: String? = null,
)

@Serializable
data class DataSource(
    val localPath: String? = null,
    val remoteUrl: String? = null,
) {
    val displayPath: String? get() = localPath ?: remoteUrl
}

@Serializable
sealed class ContentItem {
    abstract val id: String

    @Serializable
    data class Text(
        override val id: String = UUID.randomUUID().toString(),
        val text: String,
        val format: TextFormat = TextFormat.PLAIN,
    ) : ContentItem() {
        constructor(
            text: String,
            format: TextFormat = TextFormat.PLAIN,
        ) : this(
            id = UUID.randomUUID().toString(),
            text = text,
            format = format,
        )
    }

    @Serializable
    data class Image(
        override val id: String = UUID.randomUUID().toString(),
        val source: DataSource,
        val mimeType: String,
        val width: Int? = null,
        val height: Int? = null,
    ) : ContentItem() {
        constructor(
            source: DataSource,
            mimeType: String,
            width: Int? = null,
            height: Int? = null,
        ) : this(
            id = UUID.randomUUID().toString(),
            source = source,
            mimeType = mimeType,
            width = width,
            height = height,
        )
    }

    @Serializable
    data class File(
        override val id: String = UUID.randomUUID().toString(),
        val source: DataSource,
        val mimeType: String,
        val name: String,
        val size: Long? = null,
    ) : ContentItem() {
        constructor(
            source: DataSource,
            mimeType: String,
            name: String,
            size: Long? = null,
        ) : this(
            id = UUID.randomUUID().toString(),
            source = source,
            mimeType = mimeType,
            name = name,
            size = size,
        )
    }

    @Serializable
    data class Link(
        override val id: String = UUID.randomUUID().toString(),
        val url: String,
        val title: String? = null,
    ) : ContentItem() {
        constructor(
            url: String,
            title: String? = null,
        ) : this(
            id = UUID.randomUUID().toString(),
            url = url,
            title = title,
        )
    }
}

@Serializable
enum class TextFormat {
    PLAIN,
    MARKDOWN,
    HTML,
}
