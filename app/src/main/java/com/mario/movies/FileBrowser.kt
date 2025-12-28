package com.mario.movies

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.mario.movies.data.DatabaseHelper
import com.mario.movies.data.DatabaseUpdater
import com.mario.movies.data.FileItem
import com.mario.movies.data.Links
import com.mario.movies.data.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FileBrowser(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dbHelper = remember { DatabaseHelper.getInstance(context) }
    val tokenManager = remember { TokenManager(context) }
    val dbUpdater = remember { DatabaseUpdater(context) }
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    
    // Set the starting root path
    val rootPath = "/69/USER MARIO"
    var currentPath by remember { mutableStateOf(rootPath) }
    
    // Search states
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Dialog states
    var showDialog by remember { mutableStateOf(false) }
    var dialogItem by remember { mutableStateOf<FileItem?>(null) }
    var generatedLinks by remember { mutableStateOf<Links?>(null) }
    var isGeneratingLink by remember { mutableStateOf(false) }

    // Update state
    var isCheckingUpdates by remember { mutableStateOf(true) }
    var updateTrigger by remember { mutableStateOf(0) } // Used to refresh items after update

    LaunchedEffect(Unit) {
        dbUpdater.checkAndUpdateDatabase()
        isCheckingUpdates = false
        updateTrigger++
    }

    // Handle system back button
    BackHandler(enabled = currentPath != rootPath || isSearching) {
        if (isSearching) {
            isSearching = false
            searchQuery = ""
        } else if (currentPath != rootPath) {
            val parent = currentPath.substringBeforeLast('/')
            // Ensure we don't go beyond the rootPath
            currentPath = if (parent.length >= rootPath.length) {
                 parent
            } else {
                 rootPath
            }
        }
    }

    var items by remember { mutableStateOf<List<FileItem>?>(null) } // Null indicates loading
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var debugInfo by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentPath, isSearching, searchQuery, updateTrigger, isCheckingUpdates) {
        if (isCheckingUpdates) return@LaunchedEffect
        
        items = null // Start loading
        errorMsg = null
        debugInfo = null
        try {
            val fetchedItems = withContext(Dispatchers.IO) {
                if (isSearching && searchQuery.isNotEmpty()) {
                    dbHelper.searchItems(searchQuery)
                } else if (isSearching && searchQuery.isEmpty()) {
                    emptyList() // Don't show anything or show empty list if searching but empty query
                } else {
                    dbHelper.getItemsByPath(currentPath)
                }
            }
            items = fetchedItems
            if (fetchedItems.isEmpty() && !isSearching) {
                 debugInfo = withContext(Dispatchers.IO) {
                    dbHelper.getDebugInfo()
                }
            }
        } catch (e: Exception) {
            errorMsg = e.message
            items = emptyList()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        if (isSearching) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { 
                    isSearching = false 
                    searchQuery = ""
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                         Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear"
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentPath != rootPath) {
                    IconButton(onClick = { 
                        val parent = currentPath.substringBeforeLast('/')
                        // Ensure we don't go beyond the rootPath
                        currentPath = if (parent.length >= rootPath.length) {
                             parent
                        } else {
                             rootPath
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
                
                val displayTitle = if (currentPath == rootPath) "Home" else currentPath.removePrefix(rootPath).trimStart('/')
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                
                IconButton(onClick = { isSearching = true }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                }
            }
        }

        // Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val currentItems = items
            when {
                isCheckingUpdates -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Checking for database updates...")
                    }
                }
                currentItems == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                errorMsg != null -> {
                    Text(
                        text = "Error: $errorMsg",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                }
                currentItems.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isSearching) "No results found" else "No items found",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (!isSearching) {
                            Text(
                                text = "Path: $currentPath",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (debugInfo != null) {
                                Text(
                                    text = "Debug: $debugInfo",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top=8.dp)
                                )
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(currentItems) { item ->
                            FileListItem(item) {
                                if (item.isFolder) {
                                    currentPath = item.path
                                    isSearching = false
                                    searchQuery = ""
                                } else {
                                    // Handle file click
                                    dialogItem = item
                                    generatedLinks = null
                                    isGeneratingLink = false
                                    showDialog = true
                                    
                                    // Start link generation automatically
                                    isGeneratingLink = true
                                    scope.launch {
                                        val links = tokenManager.getLinks(item.id)
                                        generatedLinks = links
                                        isGeneratingLink = false
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Popup Dialog
    if (showDialog && dialogItem != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = dialogItem!!.name) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Size: ${formatSize(dialogItem!!.size)}")
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (isGeneratingLink) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.height(24.dp).padding(end = 8.dp))
                            Text("Generating links...")
                        }
                    } else if (generatedLinks != null) {
                        if (generatedLinks?.downloadLink != null) {
                            Button(
                                onClick = {
                                     try {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(Uri.parse(generatedLinks!!.downloadLink!!), "video/*")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // Handle case where no app can open the link
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Play with...")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(generatedLinks!!.downloadLink!!))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Copy Direct Link")
                            }
                        }
                        
                        if (generatedLinks?.shareLink != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(generatedLinks!!.shareLink!!))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Copy Share Link")
                            }
                        }
                    } else {
                        Text("Could not generate links. Check your internet or token.")
                        Button(
                            onClick = {
                                isGeneratingLink = true
                                scope.launch {
                                    val links = tokenManager.getLinks(dialogItem!!.id)
                                    generatedLinks = links
                                    isGeneratingLink = false
                                }
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Retry")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun FileListItem(item: FileItem, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.name) },
        supportingContent = { 
            val info = if (item.isFolder) "Folder" else formatSize(item.size)
            Text(info) 
        },
        leadingContent = {
            Icon(
                imageVector = if (item.isFolder) Icons.Default.Folder else Icons.Default.Description,
                contentDescription = null,
                tint = if (item.isFolder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(java.util.Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
