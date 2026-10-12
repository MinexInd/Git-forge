package com.projecthub.gitforge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projecthub.gitforge.core.ErrorKind
import com.projecthub.gitforge.ui.RepositoryFilter
import com.projecthub.gitforge.ui.RepositoriesViewModel
import com.projecthub.gitforge.ui.RepositoryListItem
import com.projecthub.gitforge.ui.components.BannerTone
import com.projecthub.gitforge.ui.components.DestructiveConfirmDialog
import com.projecthub.gitforge.ui.components.EmptyState
import com.projecthub.gitforge.ui.components.ErrorState
import com.projecthub.gitforge.ui.components.InfoBanner
import com.projecthub.gitforge.ui.components.InlineProgress
import com.projecthub.gitforge.ui.components.RowDivider
import com.projecthub.gitforge.ui.components.SectionHeader
import com.projecthub.gitforge.ui.components.StatusChip

/**
 * Repositories: local workspaces and GitHub repositories.
 *
 * Local and remote are shown as separate sections so a remote listing can never be
 * mistaken for something already on the device. Every remote row states whether it
 * is already cloned.
 */
@Composable
fun RepositoriesScreen(
    viewModel: RepositoriesViewModel,
    onOpenRepository: (Long) -> Unit,
    onGoToSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<RepositoryListItem?>(null) }

    LaunchedEffect(state.gitHubConnected) {
        if (state.gitHubConnected && state.remoteRepositories.isEmpty() && state.remoteError == null) {
            viewModel.loadRemoteRepositories(reset = true)
        }
    }

    // Load the next page when the user reaches the end of the list.
    val shouldLoadMore by remember {
        androidx.compose.runtime.derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3 && state.remoteHasMore && !state.loadingRemote
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMoreRemote()
    }

    Column(Modifier.fillMaxSize()) {
        SearchAndFilterBar(
            query = state.searchQuery,
            filter = state.filter,
            onQueryChange = viewModel::onSearchQueryChange,
            onFilterChange = viewModel::onFilterChange,
            onRefresh = {
                viewModel.refreshLocal()
                if (state.gitHubConnected) viewModel.loadRemoteRepositories(reset = true)
            },
        )

        state.cloneProgress?.let { progress ->
            InlineProgress(
                message = progress,
                progress = state.cloneFraction,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        state.errorMessage?.let { message ->
            InfoBanner(
                message = message,
                tone = BannerTone.ERROR,
                actionLabel = "Dismiss",
                onAction = viewModel::clearError,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        state.message?.let { message ->
            InfoBanner(
                message = message,
                tone = BannerTone.INFO,
                actionLabel = "OK",
                onAction = viewModel::clearMessage,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        val localItems = viewModel.visibleLocalRepositories()
        val remoteItems = viewModel.visibleRemoteRepositories()

        if (state.loadingLocal && localItems.isEmpty() && remoteItems.isEmpty()) {
            com.projecthub.gitforge.ui.components.LoadingState("Loading repositories…")
            return@Column
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("New local project")
                    }
                }
            }

            item { SectionHeader("On this device") }
            if (localItems.isEmpty()) {
                item {
                    Text(
                        text = if (state.filter == RepositoryFilter.LOCAL) {
                            "No local repositories yet. Create one or clone a GitHub repository."
                        } else {
                            "No local repositories match the current filter."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(localItems, key = { it.entity.id }) { item ->
                    LocalRepositoryRow(
                        item = item,
                        onOpen = { onOpenRepository(item.entity.id) },
                        onDelete = { deleteTarget = item },
                    )
                    RowDivider()
                }
            }

            item { SectionHeader("GitHub") }
            when {
                !state.gitHubConnected -> item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Not connected", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Connect a GitHub account to browse, clone, pull and push your repositories.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onGoToSettings) { Text("Connect GitHub") }
                        }
                    }
                }

                state.remoteError != null -> item {
                    ErrorState(
                        title = if (state.remoteErrorKind == ErrorKind.AUTH) "Authorization needed" else "Could not load repositories",
                        message = state.remoteError ?: "",
                        onRetry = { viewModel.loadRemoteRepositories(reset = true) },
                        modifier = Modifier.height(280.dp),
                    )
                }

                state.loadingRemote && remoteItems.isEmpty() -> item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        androidx.compose.material3.CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Loading GitHub repositories…", style = MaterialTheme.typography.bodySmall)
                    }
                }

                remoteItems.isEmpty() -> item {
                    Text(
                        text = if (state.searchQuery.isBlank()) {
                            "No repositories are visible to this account."
                        } else {
                            "No repositories match \"${state.searchQuery}\"."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                else -> items(remoteItems, key = { it.id }) { repo ->
                    val alreadyCloned = state.localRepositories.any {
                        it.entity.ownerName.equals(repo.fullName, ignoreCase = true)
                    }
                    RemoteRepositoryRow(
                        name = repo.name,
                        fullName = repo.fullName,
                        description = repo.description,
                        isPrivate = repo.private,
                        defaultBranch = repo.defaultBranch,
                        language = repo.language,
                        alreadyCloned = alreadyCloned,
                        cloneInProgress = state.cloneProgress != null,
                        onClone = { viewModel.cloneRepository(repo) },
                        onOpen = {
                            state.localRepositories
                                .firstOrNull { it.entity.ownerName.equals(repo.fullName, ignoreCase = true) }
                                ?.let { onOpenRepository(it.entity.id) }
                        },
                    )
                    RowDivider()
                }
            }

            if (state.loadingRemote && remoteItems.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateLocalProjectDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, initGit ->
                viewModel.createLocalRepository(name, initGit)
                showCreateDialog = false
            },
        )
    }

    deleteTarget?.let { target ->
        DestructiveConfirmDialog(
            title = "Delete ${target.entity.displayName}?",
            explanation = "This permanently removes the working tree from app storage, including any uncommitted " +
                "changes and unpushed commits. Remote copies on GitHub are not affected.",
            confirmLabel = "Delete repository",
            detail = target.entity.localPath,
            onConfirm = {
                viewModel.deleteRepository(target)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun SearchAndFilterBar(
    query: String,
    filter: RepositoryFilter,
    onQueryChange: (String) -> Unit,
    onFilterChange: (RepositoryFilter) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Search repositories") },
            trailingIcon = {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh repository list")
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RepositoryFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilterChange(option) },
                    label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }
    }
}

@Composable
private fun LocalRepositoryRow(
    item: RepositoryListItem,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (item.entity.isLocalOnly) Icons.Default.Folder else Icons.Default.CloudDownload,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.entity.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${item.entity.currentBranch.ifBlank { "no branch" }} · ${formatBytes(item.entity.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!item.existsOnDisk) {
                Spacer(Modifier.height(2.dp))
                StatusChip(label = "Missing on disk", color = MaterialTheme.colorScheme.error)
            } else {
                val changed = item.status?.files?.size ?: 0
                if (changed > 0) {
                    Spacer(Modifier.height(2.dp))
                    StatusChip(label = "$changed uncommitted", color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
        TextButton(onClick = onOpen) { Text("Open") }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete ${item.entity.displayName} from this device",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun RemoteRepositoryRow(
    name: String,
    fullName: String,
    description: String?,
    isPrivate: Boolean,
    defaultBranch: String,
    language: String?,
    alreadyCloned: Boolean,
    cloneInProgress: Boolean,
    onClone: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isPrivate) Icons.Default.Lock else Icons.Default.Public,
            contentDescription = if (isPrivate) "Private repository" else "Public repository",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = fullName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(label = defaultBranch, color = MaterialTheme.colorScheme.secondary)
                if (!language.isNullOrBlank()) {
                    StatusChip(label = language, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        if (alreadyCloned) {
            TextButton(onClick = onOpen) { Text("Open") }
        } else {
            OutlinedButton(onClick = onClone, enabled = !cloneInProgress) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Clone")
            }
        }
    }
}

@Composable
private fun CreateLocalProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var initialiseGit by remember { mutableStateOf(true) }
    val valid = name.isNotBlank() && com.projecthub.gitforge.core.PathSafety.isSafeRepoFolderName(name)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New local project") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project name") },
                    singleLine = true,
                    isError = name.isNotBlank() && !valid,
                    supportingText = {
                        if (name.isNotBlank() && !valid) {
                            Text("Use letters, digits, dot, dash or underscore.")
                        } else {
                            Text("Created inside app storage at workspace/local/")
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Switch(
                        checked = initialiseGit,
                        onCheckedChange = { initialiseGit = it },
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Initialise a Git repository", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim(), initialiseGit) }, enabled = valid) {
                Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Small chip row used to show a repository's quick facts. */
@Composable
internal fun FactChip(label: String) {
    AssistChip(onClick = {}, label = { Text(label) })
}
