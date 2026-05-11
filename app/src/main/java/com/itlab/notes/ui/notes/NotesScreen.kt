package com.itlab.notes.ui.notes

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun notesListScreen(
    directoryName: String,
    notes: List<NoteItemUi>,
    directories: List<DirectoryItemUi>,
    actions: NotesListActions,
) {
    val colors = MaterialTheme.colorScheme

    Scaffold(
        containerColor = colors.background,
        topBar = {
            notesTopBar(
                directoryName = directoryName,
                onBack = actions.onBack,
            )
        },
        floatingActionButton = {
            notesFab(onAddNoteClick = actions.onAddNoteClick)
        },
    ) { paddingValues ->
        notesListContent(
            notes = notes,
            paddingValues = paddingValues,
            directories = directories,
            actions =
                NotesListContentActions(
                    onNoteDelete = actions.onNoteDelete,
                    onNoteMove = actions.onNoteMove,
                    onNoteClick = actions.onNoteClick,
                ),
        )
    }
}

data class NotesListActions(
    val onBack: () -> Unit,
    val onAddNoteClick: () -> Unit,
    val onNoteDelete: (NoteItemUi) -> Unit,
    val onNoteMove: (noteId: String, directoryId: String) -> Unit,
    val onNoteClick: (NoteItemUi) -> Unit,
)

private data class NotesListContentActions(
    val onNoteDelete: (NoteItemUi) -> Unit,
    val onNoteMove: (noteId: String, directoryId: String) -> Unit,
    val onNoteClick: (NoteItemUi) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun notesTopBar(
    directoryName: String,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    CenterAlignedTopAppBar(
        title = { Text(directoryName, color = colors.onSurface) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = colors.onSurface,
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Unspecified,
                navigationIconContentColor = Color.Unspecified,
                titleContentColor = Color.Unspecified,
                actionIconContentColor = Color.Unspecified,
            ),
    )
}

@Composable
private fun notesFab(onAddNoteClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FloatingActionButton(
        onClick = onAddNoteClick,
        containerColor = colors.primary,
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = colors.onPrimary,
        )
    }
}

@Composable
private fun notesListContent(
    notes: List<NoteItemUi>,
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    directories: List<DirectoryItemUi>,
    actions: NotesListContentActions,
) {
    var pendingMoveNote by remember { mutableStateOf<NoteItemUi?>(null) }
    Column(
        modifier =
            Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
    ) {
        searchField()

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            items(
                items = notes,
                key = { note -> note.id },
            ) { note ->
                notesListItem(
                    note = note,
                    onDelete = { actions.onNoteDelete(note) },
                    onClick = { actions.onNoteClick(note) },
                    onMoveRequest = { pendingMoveNote = note },
                )
            }
        }
    }
    pendingMoveNote?.let { note ->
        moveNoteDialog(
            directories = directories,
            onMoveTo = { directoryId ->
                actions.onNoteMove(note.id, directoryId)
                pendingMoveNote = null
            },
            onDismiss = { pendingMoveNote = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun notesListItem(
    note: NoteItemUi,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    onMoveRequest: () -> Unit,
) {
    var isDeleteDispatched by remember(note.id) { mutableStateOf(false) }
    val dismissState =
        rememberSwipeToDismissBoxState(
            positionalThreshold = { totalDistance -> totalDistance * 0.22f },
        )
    val swipeProgress by
        remember(dismissState) {
            derivedStateOf {
                dismissState.progress.coerceIn(0f, 1f)
            }
        }
    val swipeOffsetPx by
        remember(dismissState) {
            derivedStateOf {
                kotlin.runCatching { abs(dismissState.requireOffset()) }.getOrDefault(0f)
            }
        }
    LaunchedEffect(dismissState.targetValue, isDeleteDispatched) {
        if (!isDeleteDispatched && dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
            isDeleteDispatched = true
            onDelete()
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            swipeDeleteBackground(
                isActive = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart,
                swipeProgress = swipeProgress,
                swipeOffsetPx = swipeOffsetPx,
            )
        },
    ) {
        noteCard(
            note = note,
            onClick = onClick,
            onLongClick = onMoveRequest,
        )
    }
}

@Composable
private fun swipeDeleteBackground(
    isActive: Boolean,
    swipeProgress: Float,
    swipeOffsetPx: Float,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val clampedProgress = swipeProgress.coerceIn(0f, 1f)
    val activeScale by animateFloatAsState(
        targetValue = if (isActive) 1f else (0.9f + clampedProgress * 0.1f),
        label = "deleteIconScale",
    )
    val activeAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else (0.62f + clampedProgress * 0.28f),
        label = "deleteIconAlpha",
    )
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(vertical = 1.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        val maxWidth = maxWidth
        val gapFromCard = 8.dp
        val targetWidth =
            with(density) { (swipeOffsetPx - gapFromCard.toPx()).coerceAtLeast(0f).toDp() }
                .coerceAtMost(maxWidth)
        val animatedWidth by animateDpAsState(targetValue = targetWidth, label = "deleteBackgroundWidth")
        Surface(
            color = colors.errorContainer.copy(alpha = 0.6f),
            shape = RoundedCornerShape(16.dp),
            modifier =
                Modifier
                    .fillMaxHeight()
                    .width(animatedWidth),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = colors.onErrorContainer.copy(alpha = activeAlpha),
                    modifier =
                        Modifier.graphicsLayer(
                            scaleX = activeScale,
                            scaleY = activeScale,
                        ),
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun noteCard(
    note: NoteItemUi,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(
                text = note.title,
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = note.content,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun moveNoteDialog(
    directories: List<DirectoryItemUi>,
    onMoveTo: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move note") },
        text = {
            Column {
                directories.forEach { dir ->
                    TextButton(
                        onClick = { onMoveTo(dir.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(dir.name)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun searchField() {
    val colors = MaterialTheme.colorScheme

    Surface(
        color = colors.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(24.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Menu,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
            Text(
                text = "Hinted search text",
                color = colors.onSurfaceVariant,
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp)
                        .weight(1f),
            )
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
        }
    }
}
