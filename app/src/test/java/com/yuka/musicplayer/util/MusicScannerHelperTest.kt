package com.yuka.musicplayer.util

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MusicScannerHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testScannerClassifiesSupportedAndUnsupportedAudioFiles() {
        val root = tempFolder.newFolder("MusicTest")
        val artistDir = File(root, "Artist A/Album 1").apply { mkdirs() }
        val flacFile = File(artistDir, "song1.flac").apply { createNewFile() }
        val wavFile = File(artistDir, "song2.wav").apply { createNewFile() }
        val mp3File = File(artistDir, "song3.mp3").apply { createNewFile() }
        val m4aFile = File(artistDir, "song4.m4a").apply { createNewFile() }
        val imageFile = File(artistDir, "cover.jpg").apply { createNewFile() }
        val hiddenDir = File(root, ".thumbnails").apply { mkdirs() }
        File(hiddenDir, "thumb.flac").apply { createNewFile() }

        val scanned = MusicScannerHelper.scanMusicDirectory(root)

        assertEquals(4, scanned.size) // 2 supported + 2 unsupported audio
        val flacItem = scanned.first { it.file.name == "song1.flac" }
        assertTrue(flacItem.isSupported)
        assertEquals("FLAC", flacItem.formatLabel)
        assertEquals("Artist A / Album 1", flacItem.breadcrumb)

        val mp3Item = scanned.first { it.file.name == "song3.mp3" }
        assertFalse(mp3Item.isSupported)
        assertEquals("MP3", mp3Item.formatLabel)

        // Verify playable extraction ignores MP3/M4A
        val playable = MusicScannerHelper.filterPlayableFiles(scanned)
        assertEquals(2, playable.size)
        val playablePaths = playable.map { it.canonicalPath }.toSet()
        assertTrue(playablePaths.contains(flacFile.canonicalPath))
        assertTrue(playablePaths.contains(wavFile.canonicalPath))
        assertFalse(playablePaths.contains(mp3File.canonicalPath))
        assertFalse(playablePaths.contains(imageFile.canonicalPath))
    }

    @Test
    fun testBreadcrumbExtraction() {
        val root = File("/sdcard/Music")
        val track = File("/sdcard/Music/Deep/Nested/Folder/track.flac")
        val breadcrumb = MusicScannerHelper.getRelativeBreadcrumb(track, root)
        assertEquals("Deep / Nested / Folder", breadcrumb)

        val rootTrack = File("/sdcard/Music/root_track.flac")
        val rootBreadcrumb = MusicScannerHelper.getRelativeBreadcrumb(rootTrack, root)
        assertEquals("", rootBreadcrumb)
    }

    @Test
    fun testNomediaFolderInsideSelectedRootIsScanned() {
        val root = tempFolder.newFolder("MusicNomediaTest")
        val normalDir = File(root, "Normal").apply { mkdirs() }
        File(normalDir, "song1.flac").apply { createNewFile() }

        val audiophileDir = File(root, "Audiophile").apply { mkdirs() }
        File(audiophileDir, ".nomedia").apply { createNewFile() }
        File(audiophileDir, "hires.flac").apply { createNewFile() }

        val scanned = MusicScannerHelper.scanMusicDirectory(root)
        assertEquals(2, scanned.size)
        val names = scanned.map { it.file.name }.toSet()
        assertTrue(names.contains("song1.flac"))
        assertTrue(names.contains("hires.flac"))
    }

    @Test
    fun testHiddenDotDirectoryIsIgnored() {
        val root = tempFolder.newFolder("HiddenDirTest")
        val normalDir = File(root, "Normal").apply { mkdirs() }
        File(normalDir, "song1.flac").apply { createNewFile() }

        val hiddenDir = File(root, ".thumbnails").apply { mkdirs() }
        File(hiddenDir, "thumb.flac").apply { createNewFile() }

        val scanned = MusicScannerHelper.scanMusicDirectory(root)
        assertEquals(1, scanned.size)
        assertEquals("song1.flac", scanned[0].file.name)
    }

    @Test
    fun testScannerHandlesMixedCaseExtensions() {
        val root = tempFolder.newFolder("MixedCaseTest")
        File(root, "TEST1.FLAC").apply { createNewFile() }
        File(root, "TEST2.Wav").apply { createNewFile() }
        File(root, "TEST3.Mp3").apply { createNewFile() }

        val scanned = MusicScannerHelper.scanMusicDirectory(root)
        assertEquals(3, scanned.size)
        assertTrue(scanned.first { it.file.name == "TEST1.FLAC" }.isSupported)
        assertTrue(scanned.first { it.file.name == "TEST2.Wav" }.isSupported)
        assertFalse(scanned.first { it.file.name == "TEST3.Mp3" }.isSupported)
    }

    @Test
    fun testScannerHandlesRootWithTrailingSlashAndDeepNesting() {
        val root = tempFolder.newFolder("DeepTest")
        val deep = File(root, "Level1/Level2/Level3").apply { mkdirs() }
        File(deep, "deep.flac").apply { createNewFile() }

        val scanned = MusicScannerHelper.scanMusicDirectory(File(root.absolutePath + "/"))
        assertEquals(1, scanned.size)
        assertEquals("Level1 / Level2 / Level3", scanned[0].breadcrumb)
    }

    @Test
    fun testGetRelativeBreadcrumbRejectsSiblingFolderWithSamePrefix() {
        val root = File("/storage/emulated/0/Music")
        val siblingTrack = File("/storage/emulated/0/Music2/Rock/song.flac")
        val breadcrumb = MusicScannerHelper.getRelativeBreadcrumb(siblingTrack, root)
        assertEquals("", breadcrumb)
    }

    @Test
    fun testScannerExtractsVorbisTagsIntoScannedMusicItem() {
        val root = tempFolder.newFolder("TagScanTest")
        val flacFile = File(root, "tagged.flac")

        val bos = java.io.ByteArrayOutputStream()
        bos.write("fLaC".toByteArray(Charsets.US_ASCII))
        // Streaminfo (type 0, not last)
        bos.write(0x00)
        bos.write(0x00); bos.write(0x00); bos.write(34)
        bos.write(ByteArray(34))

        // Vorbis comment (type 4, is last = 0x84)
        val vcBos = java.io.ByteArrayOutputStream()
        val vendor = "TagTest".toByteArray(Charsets.UTF_8)
        vcBos.write(vendor.size and 0xFF)
        vcBos.write((vendor.size shr 8) and 0xFF)
        vcBos.write((vendor.size shr 16) and 0xFF)
        vcBos.write((vendor.size shr 24) and 0xFF)
        vcBos.write(vendor)

        val comments = listOf(
            "TITLE=Stairway to Heaven",
            "ARTIST=Led Zeppelin",
            "ALBUM=Led Zeppelin IV",
            "GENRE=Classic Rock",
            "DATE=1971",
            "TRACKNUMBER=4"
        )
        vcBos.write(comments.size and 0xFF)
        vcBos.write((comments.size shr 8) and 0xFF)
        vcBos.write((comments.size shr 16) and 0xFF)
        vcBos.write((comments.size shr 24) and 0xFF)
        for (c in comments) {
            val bytes = c.toByteArray(Charsets.UTF_8)
            vcBos.write(bytes.size and 0xFF)
            vcBos.write((bytes.size shr 8) and 0xFF)
            vcBos.write((bytes.size shr 16) and 0xFF)
            vcBos.write((bytes.size shr 24) and 0xFF)
            vcBos.write(bytes)
        }

        val vcData = vcBos.toByteArray()
        bos.write(0x84)
        bos.write((vcData.size shr 16) and 0xFF)
        bos.write((vcData.size shr 8) and 0xFF)
        bos.write(vcData.size and 0xFF)
        bos.write(vcData)

        flacFile.writeBytes(bos.toByteArray())

        val scanned = MusicScannerHelper.scanMusicDirectory(root)
        assertEquals(1, scanned.size)
        val item = scanned[0]
        assertEquals("Stairway to Heaven", item.title)
        assertEquals("Led Zeppelin", item.artist)
        assertEquals("Led Zeppelin IV", item.album)
        assertEquals("Classic Rock", item.genre)
        assertEquals(1971, item.year)
        assertEquals(4, item.trackNumber)
    }

    @Test
    fun testDiscoverAudioFilesResilientReturnsDirectFilesAndDeduplicates() {
        val root = tempFolder.newFolder("ResilientScanTest")
        val albumDir = File(root, "Artist/Album").apply { mkdirs() }
        val f1 = File(albumDir, "01.flac").apply { createNewFile() }
        val f2 = File(albumDir, "02.wav").apply { createNewFile() }
        val f3 = File(albumDir, "03.mp3").apply { createNewFile() }
        val f4 = File(albumDir, "lyrics.lrc").apply { createNewFile() }
        val f5 = File(albumDir, ".nomedia").apply { createNewFile() }

        val discovered = MusicScannerHelper.discoverAudioFilesResilient(root, context = null, isForceRescan = false)
        assertEquals(3, discovered.size) // flac, wav, mp3 (lrc and .nomedia excluded)
        val names = discovered.map { it.name }.toSet()
        assertTrue(names.contains("01.flac"))
        assertTrue(names.contains("02.wav"))
        assertTrue(names.contains("03.mp3"))
        assertFalse(names.contains("lyrics.lrc"))
    }

    @Test
    fun testScanMusicDirectoryWithForceRescanFlag() {
        val root = tempFolder.newFolder("ForceRescanTest")
        val albumDir = File(root, "Album").apply { mkdirs() }
        File(albumDir, "track.flac").apply { createNewFile() }

        val scanned = MusicScannerHelper.scanMusicDirectory(root, context = null, isForceRescan = true)
        assertEquals(1, scanned.size)
        assertEquals("track.flac", scanned[0].file.name)
    }

    @Test
    fun testEscapeSqlLike() {
        assertEquals("/storage/emulated/0/Music", MusicScannerHelper.escapeSqlLike("/storage/emulated/0/Music"))
        assertEquals("/storage/emulated/0/AC\\_DC/100\\%", MusicScannerHelper.escapeSqlLike("/storage/emulated/0/AC_DC/100%"))
        assertEquals("path\\\\with\\\\backslash", MusicScannerHelper.escapeSqlLike("path\\with\\backslash"))
    }

    @Test
    fun testResolveSafDocumentIdToFile() {
        val primaryResolved = MusicScannerHelper.resolveSafDocumentIdToFile("primary:Music/Album/song.flac")
        assertNotNull(primaryResolved)
        assertTrue(primaryResolved!!.path.endsWith("Music/Album/song.flac"))

        val sdcardResolved = MusicScannerHelper.resolveSafDocumentIdToFile("1234-5678:HiRes/track.flac")
        assertNotNull(sdcardResolved)
        assertEquals("/storage/1234-5678/HiRes/track.flac", sdcardResolved!!.path)

        val directResolved = MusicScannerHelper.resolveSafDocumentIdToFile("/storage/emulated/0/Music/song.wav")
        assertNotNull(directResolved)
        assertEquals("/storage/emulated/0/Music/song.wav", directResolved!!.path)

        assertNull(MusicScannerHelper.resolveSafDocumentIdToFile(""))
    }

    @Test
    fun testFindBlockingNomediaFilesDetectsAncestorNomedia() {
        val root = tempFolder.newFolder("NomediaAncestorTest")
        val ancestorDir = File(root, "Ancestor").apply { mkdirs() }
        val nomediaFile = File(ancestorDir, ".nomedia").apply { createNewFile() }
        val musicChild = File(ancestorDir, "Sub/ChildMusic").apply { mkdirs() }

        val blocking = MusicScannerHelper.findBlockingNomediaFiles(musicChild)
        assertEquals(1, blocking.size)
        assertEquals(MusicScannerHelper.canonicalizeFile(nomediaFile).absolutePath, blocking[0])

        val cleanDir = File(root, "Clean/Music").apply { mkdirs() }
        val cleanBlocking = MusicScannerHelper.findBlockingNomediaFiles(cleanDir)
        assertTrue(cleanBlocking.isEmpty())
    }

    @Test
    fun testScanDiagnosticsSummaryPopulatedAfterScan() {
        val root = tempFolder.newFolder("DiagnosticsSummaryTest")
        val album = File(root, "HiRes").apply { mkdirs() }
        File(album, "audio.flac").apply { createNewFile() }

        MusicScannerHelper.discoverAudioFilesResilient(root, context = null, isForceRescan = true)
        val diag = MusicScannerHelper.lastScanDiagnostics
        assertNotNull(diag)
        assertEquals(1, diag!!.totalDiscovered)
        assertTrue(diag.elapsedMs >= 0)
        assertEquals(1, diag.layer1WalkCount)
    }

    @Test
    fun testBuildFolderBrowserSlicePureRamOnVirtualPathsAndSorting() {
        // Pure virtual paths that DO NOT exist on disk -> proves 0 FUSE/disk syscalls!
        val rootDir = File("/storage/emulated/0/VirtualMusic")

        fun fakeItem(relPath: String, supported: Boolean = true, lastMod: Long = 1000L): com.yuka.musicplayer.model.ScannedMusicItem {
            val file = File(rootDir, relPath)
            val ext = file.extension.uppercase()
            return com.yuka.musicplayer.model.ScannedMusicItem.createWithPrecomputedDisplay(
                file = file,
                isSupported = supported,
                formatLabel = ext,
                breadcrumb = "",
                title = file.nameWithoutExtension,
                artist = "",
                album = "",
                albumArtist = "",
                genre = "",
                trackNumber = 0,
                durationMs = 0L,
                year = 0,
                composer = "",
                fileSize = 1024L,
                sampleRateHz = 44100,
                bitDepth = 16,
                bitrateKbps = 1000,
                dateAdded = lastMod,
                dateModified = lastMod
            )
        }

        val items = listOf(
            fakeItem("root_direct.flac", supported = true, lastMod = 100L),
            fakeItem("Rock/AlbumA/01.flac", supported = true, lastMod = 500L),
            fakeItem("Rock/AlbumA/02.wav", supported = true, lastMod = 700L),
            fakeItem("Rock/AlbumB/03.mp3", supported = false, lastMod = 900L),
            fakeItem("Jazz/Miles.flac", supported = true, lastMod = 2000L),
            fakeItem("Ambient/Deep/Ocean/wave.flac", supported = true, lastMod = 300L)
        )

        // 1. Test Root Slice (using /sdcard alias to verify pure-RAM alias normalization!)
        val sdcardAliasRoot = File("/sdcard/VirtualMusic")
        val rootSlice = MusicScannerHelper.buildFolderBrowserSlice(
            rootDir = sdcardAliasRoot,
            currentDir = sdcardAliasRoot,
            scannedItems = items,
            folderSortField = com.yuka.musicplayer.model.FolderSortField.NAME,
            folderSortOrder = com.yuka.musicplayer.model.SortOrder.ASCENDING
        )

        assertTrue(rootSlice.isRoot)
        assertNull(rootSlice.parentDirectory)
        assertEquals(1, rootSlice.directTracks.size)
        assertEquals("root_direct.flac", rootSlice.directTracks[0].file.name)
        assertEquals(6, rootSlice.totalRecursiveMusicCount)
        assertEquals(5, rootSlice.allRecursivePlayableFiles.size) // 5 supported (excludes 03.mp3)

        assertEquals(3, rootSlice.subfolders.size)
        assertEquals(listOf("Ambient", "Jazz", "Rock"), rootSlice.subfolders.map { it.name })

        val rockNode = rootSlice.subfolders.first { it.name == "Rock" }
        assertEquals(3, rockNode.recursiveMusicCount)
        assertEquals(2, rockNode.recursivePlayableFiles.size) // 01.flac & 02.wav only
        assertEquals(0, rockNode.directMusicCount)
        assertEquals(2, rockNode.subfoldersCount) // AlbumA & AlbumB
        assertEquals(900L, rockNode.lastModified)

        val jazzNode = rootSlice.subfolders.first { it.name == "Jazz" }
        assertEquals(1, jazzNode.directMusicCount)
        assertEquals(1, jazzNode.recursiveMusicCount)
        assertEquals(0, jazzNode.subfoldersCount)
        assertEquals(2000L, jazzNode.lastModified)

        // 2. Test Entering Subfolder "Rock"
        val rockSlice = MusicScannerHelper.buildFolderBrowserSlice(
            rootDir = rootDir,
            currentDir = rockNode.directory,
            scannedItems = items
        )
        assertFalse(rockSlice.isRoot)
        assertEquals(rootDir.path, rockSlice.parentDirectory?.path)
        assertEquals(0, rockSlice.directTracks.size)
        assertEquals(2, rockSlice.subfolders.size)
        assertEquals(listOf("AlbumA", "AlbumB"), rockSlice.subfolders.map { it.name })
        assertEquals(2, rockSlice.allRecursivePlayableFiles.size)

        // 3. Test Decoupled Pure-RAM sortFolderNodes (TRACK_COUNT & DATE_MODIFIED ASC/DESC)
        val byCountDesc = MusicScannerHelper.sortFolderNodes(
            rootSlice.subfolders,
            com.yuka.musicplayer.model.FolderSortField.TRACK_COUNT,
            com.yuka.musicplayer.model.SortOrder.DESCENDING
        )
        assertEquals("Rock", byCountDesc.first().name) // 3 tracks

        val byDateDesc = MusicScannerHelper.sortFolderNodes(
            rootSlice.subfolders,
            com.yuka.musicplayer.model.FolderSortField.DATE_MODIFIED,
            com.yuka.musicplayer.model.SortOrder.DESCENDING
        )
        assertEquals(listOf("Jazz", "Rock", "Ambient"), byDateDesc.map { it.name }) // 2000, 900, 300

        val byNameDesc = MusicScannerHelper.sortFolderNodes(
            rootSlice.subfolders,
            com.yuka.musicplayer.model.FolderSortField.NAME,
            com.yuka.musicplayer.model.SortOrder.DESCENDING
        )
        assertEquals(listOf("Rock", "Jazz", "Ambient"), byNameDesc.map { it.name })
    }
}

