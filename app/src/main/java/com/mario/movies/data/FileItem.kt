package com.mario.movies.data

data class FileItem(
    val id: String,
    val name: String,
    val path: String,
    val size: Long,
    val type: String, // "file" or "folder"
    val lastModified: String,
    val deleted: Int,
    val syncedAt: Long?
) {
    val isFolder: Boolean get() = type == "folder"
}
