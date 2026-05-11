package com.itlab.notes.ui.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun directoriesScreen(
    directories: List<DirectoryItemUi> = previewDirectoriesFallback(),
    onCreateDirectory: (String) -> Unit,
    onDeleteDirectory: (DirectoryItemUi) -> Unit,
    onRenameDirectory: (DirectoryItemUi, String) -> Unit,
    onDirectoryClick: (DirectoryItemUi) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            directoriesTopBar(
                onAddDirectoryClick = { showCreateDialog = true },
            )
        },
    ) { paddingValues ->
        directoriesList(
            directories = directories,
            onDirectoryLongClick = onDeleteDirectory,
            onDirectoryRename = onRenameDirectory,
            onDirectoryClick = onDirectoryClick,
            modifier = Modifier.padding(paddingValues),
        )
    }
    if (showCreateDialog) {
        var directoryName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Directory") },
            text = {
                OutlinedTextField(
                    value = directoryName,
                    onValueChange = { directoryName = it },
                    label = { Text("Directory name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreateDirectory(directoryName)
                        showCreateDialog = false
                    },
                    enabled = directoryName.trim().isNotEmpty(),
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun directoriesTopBar(onAddDirectoryClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    CenterAlignedTopAppBar(
        title = { Text("Directories", color = colors.onSurface) },
        actions = {
            IconButton(onClick = onAddDirectoryClick) {
                Icon(
                    Icons.Default.Add,
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
private fun directoriesList(
    directories: List<DirectoryItemUi>,
    onDirectoryLongClick: (DirectoryItemUi) -> Unit,
    onDirectoryRename: (DirectoryItemUi, String) -> Unit,
    onDirectoryClick: (DirectoryItemUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    var directoryPendingDelete by remember { mutableStateOf<DirectoryItemUi?>(null) }
    var directoryPendingRename by remember { mutableStateOf<DirectoryItemUi?>(null) }
    LazyColumn(modifier = modifier.padding(horizontal = 16.dp)) {
        items(directories) { dir ->
            directoryRow(
                directory = dir,
                onClick = { onDirectoryClick(dir) },
                onLongClick = {
                    if (dir.id != "all") {
                        directoryPendingDelete = dir
                    }
                },
            )
        }
    }
    directoryPendingDelete?.let { dir ->
        directoryActionsDialog(
            directory = dir,
            onDelete = {
                onDirectoryLongClick(dir)
                directoryPendingDelete = null
            },
            onRename = {
                directoryPendingDelete = null
                directoryPendingRename = dir
            },
            onDismiss = { directoryPendingDelete = null },
        )
    }
    directoryPendingRename?.let { dir ->
        directoryRenameDialog(
            directory = dir,
            onSave = { newName ->
                onDirectoryRename(dir, newName)
                directoryPendingRename = null
            },
            onDismiss = { directoryPendingRename = null },
        )
    }
}

@Composable
private fun directoryActionsDialog(
    directory: DirectoryItemUi,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Directory actions") },
        text = { Text("Choose action for \"${directory.name}\"") },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onRename) {
                Text("Rename")
            }
        },
    )
}

@Composable
private fun directoryRenameDialog(
    directory: DirectoryItemUi,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var renameName by remember(directory.id) { mutableStateOf(directory.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename directory") },
        text = {
            OutlinedTextField(
                value = renameName,
                onValueChange = { renameName = it },
                label = { Text("Directory name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(renameName) },
                enabled = renameName.trim().isNotEmpty(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun directoryRow(
    directory: DirectoryItemUi,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Stars,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = directory.name,
            color = colors.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Surface(
            color = colors.surfaceVariant,
            shape = CircleShape,
        ) {
            Text(
                text = directory.noteCount.toString(),
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
        )
    }
}

private fun previewDirectoriesFallback(): List<DirectoryItemUi> =
    listOf(
        DirectoryItemUi(id = "all", name = "All Notes", noteCount = 0),
        DirectoryItemUi(id = "study", name = "My Study", noteCount = 0),
        DirectoryItemUi(id = "cook", name = "How to Cook", noteCount = 0),
        DirectoryItemUi(id = "poems", name = "My poems", noteCount = 0),
    )
