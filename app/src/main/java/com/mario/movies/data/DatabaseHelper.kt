package com.mario.movies.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class DatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "MoviesData.db"
        private const val ASSET_NAME = "DB.db"
        private const val DB_VERSION = 1
        private var instance: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            if (instance == null) {
                instance = DatabaseHelper(context.applicationContext)
            }
            return instance!!
        }
    }

    private var dbPath: String = context.getDatabasePath(DB_NAME).path

    override fun onCreate(db: SQLiteDatabase?) {
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
    }

    private fun ensureDatabase() {
        if (!checkDatabase()) {
            Log.d("DatabaseHelper", "Database not found, creating...")
            this.readableDatabase.close() 
            try {
                copyDatabase()
            } catch (e: IOException) {
                throw RuntimeException("Error copying database", e)
            }
        }
    }

    private fun checkDatabase(): Boolean {
        return try {
            val dbFile = context.getDatabasePath(DB_NAME)
            dbFile.exists()
        } catch (e: Exception) {
            false
        }
    }

    private fun copyDatabase() {
        try {
            val inputStream: InputStream = context.assets.open(ASSET_NAME)
            val outFileName = dbPath
            val outputStream: OutputStream = FileOutputStream(outFileName)

            val buffer = ByteArray(1024)
            var length: Int
            while (inputStream.read(buffer).also { length = it } > 0) {
                outputStream.write(buffer, 0, length)
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()
            Log.d("DatabaseHelper", "Database copied successfully to $dbPath")
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error copying database", e)
        }
    }
    
    fun getDebugInfo(): String {
        ensureDatabase()
        val sb = StringBuilder()
        val db = try {
            this.readableDatabase
        } catch (e: Exception) {
            return "Error opening DB: ${e.message}"
        }

        try {
            val countCursor = db.rawQuery("SELECT count(*) FROM items", null)
            if (countCursor.moveToFirst()) {
                sb.append("Total items: ${countCursor.getInt(0)}\n")
            }
            countCursor.close()
            
            sb.append("First 5 paths:\n")
            val sampleCursor = db.rawQuery("SELECT path FROM items LIMIT 5", null)
            if (sampleCursor.moveToFirst()) {
                do {
                     sb.append("'${sampleCursor.getString(0)}'\n")
                } while (sampleCursor.moveToNext())
            } else {
                sb.append("No items found in table.\n")
            }
            sampleCursor.close()
        } catch (e: Exception) {
            sb.append("Error querying DB: ${e.message}")
        }
        return sb.toString()
    }

    fun getItemsByPath(parentPath: String): List<FileItem> {
        ensureDatabase()

        val items = ArrayList<FileItem>()
        val db = try {
            this.readableDatabase
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error getting readable database", e)
            return emptyList()
        }
        
        val searchPath = if (parentPath == "/") "" else parentPath
        
        val allChildrenCursor = db.rawQuery("SELECT path, type, size, name, id FROM items WHERE path LIKE ? AND path != ?", arrayOf("$searchPath/%", searchPath))
        
        val processedItems = HashMap<String, FileItem>()
        
        if (allChildrenCursor.moveToFirst()) {
            val pathCol = allChildrenCursor.getColumnIndex("path")
            val typeCol = allChildrenCursor.getColumnIndex("type")
            val sizeCol = allChildrenCursor.getColumnIndex("size")
            val nameCol = allChildrenCursor.getColumnIndex("name")
            val idCol = allChildrenCursor.getColumnIndex("id")
            
            do {
                val fullPath = allChildrenCursor.getString(pathCol)
                
                val relativePath = if (searchPath.isEmpty()) fullPath.substring(1) else fullPath.substring(searchPath.length + 1)
                val slashIndex = relativePath.indexOf('/')
                
                val childName = if (slashIndex == -1) relativePath else relativePath.substring(0, slashIndex)
                val isDirectChild = slashIndex == -1
                
                val childPath = if (searchPath.isEmpty()) "/$childName" else "$searchPath/$childName"
                
                if (processedItems.containsKey(childPath)) {
                    if (isDirectChild) {
                         val id = allChildrenCursor.getString(idCol)
                         val name = allChildrenCursor.getString(nameCol)
                         val size = allChildrenCursor.getLong(sizeCol)
                         val type = allChildrenCursor.getString(typeCol)
                         processedItems[childPath] = FileItem(id, name, childPath, size, type, "", 0, 0)
                    }
                } else {
                    if (isDirectChild) {
                         val id = allChildrenCursor.getString(idCol)
                         val name = allChildrenCursor.getString(nameCol)
                         val size = allChildrenCursor.getLong(sizeCol)
                         val type = allChildrenCursor.getString(typeCol)
                         processedItems[childPath] = FileItem(id, name, childPath, size, type, "", 0, 0)
                    } else {
                        processedItems[childPath] = FileItem(childPath.hashCode().toString(), childName, childPath, 0, "folder", "", 0, 0)
                    }
                }
                
            } while (allChildrenCursor.moveToNext())
        }
        allChildrenCursor.close()
        
        items.addAll(processedItems.values.sortedWith(compareBy({ it.type != "folder" }, { it.name })))
        
        return items
    }

    fun searchItems(query: String): List<FileItem> {
        ensureDatabase()
        val items = ArrayList<FileItem>()
        val db = try {
            this.readableDatabase
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error getting readable database", e)
            return emptyList()
        }

        // Search for items where name contains the query (case-insensitive)
        val sql = "SELECT * FROM items WHERE name LIKE ? ORDER BY type DESC, name ASC"
        val params = arrayOf("%$query%")

        try {
            val cursor = db.rawQuery(sql, params)
            if (cursor.moveToFirst()) {
                val idIdx = cursor.getColumnIndex("id")
                val nameIdx = cursor.getColumnIndex("name")
                val pathIdx = cursor.getColumnIndex("path")
                val sizeIdx = cursor.getColumnIndex("size")
                val typeIdx = cursor.getColumnIndex("type")
                val lastModifiedIdx = cursor.getColumnIndex("last_modified")
                val deletedIdx = cursor.getColumnIndex("deleted")
                val syncedAtIdx = cursor.getColumnIndex("synced_at")

                do {
                    if (idIdx != -1 && nameIdx != -1 && pathIdx != -1) {
                        val id = cursor.getString(idIdx)
                        val name = cursor.getString(nameIdx)
                        val path = cursor.getString(pathIdx)
                        val size = if (sizeIdx != -1) cursor.getLong(sizeIdx) else 0L
                        val type = if (typeIdx != -1) cursor.getString(typeIdx) else "file"
                        val lastModified = if (lastModifiedIdx != -1) cursor.getString(lastModifiedIdx) else ""
                        val deleted = if (deletedIdx != -1) cursor.getInt(deletedIdx) else 0
                        val syncedAt = if (syncedAtIdx != -1 && !cursor.isNull(syncedAtIdx)) cursor.getLong(syncedAtIdx) else null

                        if (deleted == 0) {
                            items.add(FileItem(id, name, path, size, type, lastModified, deleted, syncedAt))
                        }
                    }
                } while (cursor.moveToNext())
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error searching database", e)
        }
        return items
    }
}
