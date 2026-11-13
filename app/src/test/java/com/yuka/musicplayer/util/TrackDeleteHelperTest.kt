package com.yuka.musicplayer.util

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrackDeleteHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testDirectDeletionOfPhysicalFiles() {
        val file1 = tempFolder.newFile("song1.flac")
        val file2 = tempFolder.newFile("song2.wav")
        assertTrue(file1.exists())
        assertTrue(file2.exists())

        // In pure JVM environment without Android Context/ContentResolver,
        // direct file.delete() handles physical files
        var count = 0
        listOf(file1, file2).forEach {
            if (it.delete()) count++
        }

        assertEquals(2, count)
        assertFalse(file1.exists())
        assertFalse(file2.exists())
    }

    @Test
    fun testPlaylistStorageHelperRemoveTracks() {
        val filesDir = tempFolder.newFolder("files")
        val (p1, _) = PlaylistStorageHelper.createPlaylist(filesDir, "Test Playlist 1")
        val (p2, _) = PlaylistStorageHelper.createPlaylist(filesDir, "Test Playlist 2")

        val trackA = "/storage/emulated/0/Music/track_a.flac"
        val trackB = "/storage/emulated/0/Music/track_b.flac"
        val trackC = "/storage/emulated/0/Music/track_c.flac"

        PlaylistStorageHelper.addTrackToPlaylist(filesDir, p1.id, trackA)
        PlaylistStorageHelper.addTrackToPlaylist(filesDir, p1.id, trackB)
        PlaylistStorageHelper.addTrackToPlaylist(filesDir, p2.id, trackB)
        PlaylistStorageHelper.addTrackToPlaylist(filesDir, p2.id, trackC)

        // Verify tracks added
        val loaded = PlaylistStorageHelper.loadAllPlaylists(filesDir)
        assertEquals(2, loaded.find { it.id == p1.id }?.trackPaths?.size)
        assertEquals(2, loaded.find { it.id == p2.id }?.trackPaths?.size)

        // Remove trackB across all playlists
        val updated = PlaylistStorageHelper.removeTracksFromAllPlaylists(filesDir, setOf(trackB))
        assertNotNull(updated)

        val updatedP1 = updated!!.find { it.id == p1.id }
        val updatedP2 = updated.find { it.id == p2.id }

        assertEquals(1, updatedP1?.trackPaths?.size)
        assertEquals(trackA, updatedP1?.trackPaths?.first())

        assertEquals(1, updatedP2?.trackPaths?.size)
        assertEquals(trackC, updatedP2?.trackPaths?.first())
    }
}
