package com.itlab.notes.ui.notes

data class NoteItemUi(
    val id: String,
    val title: String,
    val content: String,
    val folderId: String? = null,
)
