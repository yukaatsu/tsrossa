package com.yuka.musicplayer.util

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

object TrackDeleteHelper {

    data class DeleteExecutionResult(
        val immediatelyDeletedFiles: List<File>,
        val pendingFilesForPrompt: List<File>,
        val pendingIntentSender: IntentSender?
    )

    /**
     * Resolves MediaStore Audio content URI for a given filesystem path.
     */
    fun findAudioContentUri(context: Context, file: File): Uri? {
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA)
        val candidatePaths = listOfNotNull(
            file.absolutePath,
            try { file.canonicalPath } catch (_: Exception) { null }
        ).distinct()

        for (path in candidatePaths) {
            val selection = "${MediaStore.Audio.Media.DATA} = ?"
            val selectionArgs = arrayOf(path)
            try {
                resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                        val id = cursor.getLong(idIndex)
                        return ContentUris.withAppendedId(collection, id)
                    }
                }
            } catch (_: Exception) {
                // Ignore query error and try fallback
            }
        }
        return null
    }

    /**
     * Attempts to delete [files] from storage.
     * Handles direct file deletion, MediaStore cleanup, and Android 11+ Scoped Storage delete request.
     */
    fun deleteTracks(context: Context, files: List<File>): DeleteExecutionResult {
        val resolver = context.contentResolver
        val immediatelyDeleted = mutableListOf<File>()
        val pendingPromptFiles = mutableListOf<File>()
        val pendingUris = mutableListOf<Uri>()
        var resolvedIntentSender: IntentSender? = null

        for (file in files) {
            var deletedDirectly = false
            try {
                if (file.delete() || !file.exists()) {
                    deletedDirectly = true
                }
            } catch (_: Exception) {
                deletedDirectly = false
            }

            if (deletedDirectly) {
                immediatelyDeleted.add(file)
                // Clean up MediaStore entry if present
                val uri = findAudioContentUri(context, file)
                if (uri != null) {
                    try {
                        resolver.delete(uri, null, null)
                    } catch (_: Exception) {
                        // Ignore cleanup failure for already deleted physical file
                    }
                }
            } else {
                // File deletion blocked by Scoped Storage or OS permission
                val contentUri = findAudioContentUri(context, file)
                if (contentUri != null) {
                    var deletedViaResolver = false
                    try {
                        val rows = resolver.delete(contentUri, null, null)
                        if (rows > 0 || !file.exists()) {
                            deletedViaResolver = true
                            immediatelyDeleted.add(file)
                        }
                    } catch (secEx: SecurityException) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            pendingPromptFiles.add(file)
                            pendingUris.add(contentUri)
                        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && secEx is RecoverableSecurityException) {
                            pendingPromptFiles.add(file)
                            if (resolvedIntentSender == null) {
                                resolvedIntentSender = secEx.userAction.actionIntent.intentSender
                            }
                        }
                    } catch (_: Exception) {
                        pendingPromptFiles.add(file)
                        pendingUris.add(contentUri)
                    }

                    if (!deletedViaResolver && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !pendingUris.contains(contentUri)) {
                        pendingPromptFiles.add(file)
                        pendingUris.add(contentUri)
                    }
                } else {
                    // Content URI not indexed in MediaStore, fallback attempt
                    try {
                        if (file.delete() || !file.exists()) {
                            immediatelyDeleted.add(file)
                        }
                    } catch (_: Exception) {
                        // Failed to delete
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && pendingUris.isNotEmpty()) {
            try {
                resolvedIntentSender = MediaStore.createDeleteRequest(resolver, pendingUris).intentSender
            } catch (_: Exception) {
                // If createDeleteRequest fails, return empty sender
            }
        }

        return DeleteExecutionResult(
            immediatelyDeletedFiles = immediatelyDeleted,
            pendingFilesForPrompt = pendingPromptFiles,
            pendingIntentSender = resolvedIntentSender
        )
    }
}
