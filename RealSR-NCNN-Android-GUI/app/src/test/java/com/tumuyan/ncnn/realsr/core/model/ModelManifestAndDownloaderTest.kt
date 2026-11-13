package com.tumuyan.ncnn.realsr.core.model

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Locale

class ModelManifestAndDownloaderTest {

    @Test
    fun bundledModelManifestParsesAndValidatesAllFourteenModels() {
        val manifestFile = File("src/main/assets/model_manifest.json")
        assertTrue("Bundled model_manifest.json must exist", manifestFile.isFile)

        val manifest = ModelManifestParser.parseAndValidate(manifestFile.readText(Charsets.UTF_8))
        assertEquals(1, manifest.manifestVersion)
        assertEquals(14, manifest.models.size)

        val ids = manifest.models.map { it.id }.toSet()
        assertTrue(ids.contains("models-RealeSR-general-v3"))
        assertTrue(ids.contains("models-upconv_7_anime_style_art_rgb"))
        assertTrue(ids.contains("models-DF2K"))
        assertTrue(ids.contains("models-srmd"))
        assertTrue(ids.contains("models-MNN-RealESRGAN_x4plus_anime_6B"))
    }

    @Test
    fun manifestParserRejectsInvalidSha256OrNonHttpsUrls() {
        val badShaJson = """
            {
              "manifestVersion": 1,
              "updatedAt": "2026-11-12",
              "sourceRepo": "https://huggingface.co/tumuyan2/realsr-models",
              "models": [{
                "id": "models-test",
                "displayName": "Test",
                "description": "Test",
                "category": "PHOTO",
                "engineTool": "realsr-ncnn",
                "defaultScale": 4,
                "supportedScales": [4],
                "totalBytes": 4,
                "files": [{
                  "fileName": "x4.bin",
                  "url": "https://huggingface.co/tumuyan2/realsr-models/resolve/main/x4.bin",
                  "sizeBytes": 4,
                  "sha256": "not-a-sha256"
                }]
              }]
            }
        """.trimIndent()

        try {
            ModelManifestParser.parseAndValidate(badShaJson)
            fail("Expected IllegalArgumentException for bad SHA-256")
        } catch (_: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun downloaderResumesFromPartialFileViaHttpRangeAndInstallsVerifiedModel() = runTest {
        val tempRoot = Files.createTempDirectory("realscale_dl_resume").toFile()
        try {
            val stagingDir = File(tempRoot, "staging")
            val modelsDir = File(tempRoot, "models")

            val fullPayload = ByteArray(2048) { idx -> ((idx * 17 + 3) and 0xFF).toByte() }
            val paramPayload = "7767517\n2 2\nInput input 0 1 in0\n".toByteArray(Charsets.UTF_8)

            // Seed a partial .part file with the first 600 bytes already downloaded
            val modelStaging = File(stagingDir, "models-test-resume").apply { mkdirs() }
            File(modelStaging, "x4.bin.part").writeBytes(fullPayload.copyOfRange(0, 600))

            val item = ModelCatalogItem(
                id = "models-test-resume",
                displayName = "Resume Test",
                description = "Tests HTTP 206 resume",
                category = "PHOTO",
                engineTool = "realsr-ncnn",
                defaultScale = 4,
                supportedScales = listOf(4),
                totalBytes = (fullPayload.size + paramPayload.size).toLong(),
                files = listOf(
                    ModelFileSpec(
                        fileName = "x4.bin",
                        url = "https://huggingface.co/tumuyan2/realsr-models/resolve/main/test/x4.bin",
                        sizeBytes = fullPayload.size.toLong(),
                        sha256 = sha256(fullPayload)
                    ),
                    ModelFileSpec(
                        fileName = "x4.param",
                        url = "https://huggingface.co/tumuyan2/realsr-models/resolve/main/test/x4.param",
                        sizeBytes = paramPayload.size.toLong(),
                        sha256 = sha256(paramPayload)
                    )
                )
            )

            val observedRangeOffsets = mutableListOf<Pair<String, Long>>()
            val fakeClient = HttpRangeClient { url, rangeStart ->
                observedRangeOffsets += url.substringAfterLast('/') to rangeStart
                val bytes = if (url.endsWith("x4.bin")) fullPayload else paramPayload
                if (rangeStart > 0L) {
                    val slice = bytes.copyOfRange(rangeStart.toInt(), bytes.size)
                    HttpRangeResponse(206, slice.size.toLong(), ByteArrayInputStream(slice))
                } else {
                    HttpRangeResponse(200, bytes.size.toLong(), ByteArrayInputStream(bytes))
                }
            }

            val installer = ResumableModelInstaller(
                httpClient = fakeClient,
                usableSpaceProvider = { 512L * 1024L * 1024L }
            )

            val snapshots = mutableListOf<DownloadProgressSnapshot>()
            val installedDir = installer.downloadAndInstall(item, stagingDir, modelsDir) {
                snapshots += it
            }

            assertEquals(listOf("x4.bin" to 600L, "x4.param" to 0L), observedRangeOffsets)
            assertTrue(installedDir.isDirectory)
            assertArrayEquals(fullPayload, File(installedDir, "x4.bin").readBytes())
            assertArrayEquals(paramPayload, File(installedDir, "x4.param").readBytes())
            assertFalse("Staging dir should be cleaned after atomic install", modelStaging.exists())
            assertEquals(item.totalBytes, snapshots.last().downloadedBytes)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun downloaderRejectsBadSha256AndNeverInstallsCorruptBytes() = runTest {
        val tempRoot = Files.createTempDirectory("realscale_dl_badsha").toFile()
        try {
            val stagingDir = File(tempRoot, "staging")
            val modelsDir = File(tempRoot, "models")
            val corruptBytes = "corrupted-payload-bytes".toByteArray(Charsets.UTF_8)
            val expectedBytes = "expected-payload-bytes!".toByteArray(Charsets.UTF_8)

            val item = ModelCatalogItem(
                id = "models-corrupt",
                displayName = "Corrupt Test",
                description = "Checksum failure test",
                category = "PHOTO",
                engineTool = "realsr-ncnn",
                defaultScale = 4,
                supportedScales = listOf(4),
                totalBytes = corruptBytes.size.toLong(),
                files = listOf(
                    ModelFileSpec(
                        fileName = "x4.bin",
                        url = "https://huggingface.co/tumuyan2/realsr-models/resolve/main/corrupt/x4.bin",
                        sizeBytes = corruptBytes.size.toLong(),
                        sha256 = sha256(expectedBytes)
                    )
                )
            )

            val installer = ResumableModelInstaller(
                httpClient = { _, _ ->
                    HttpRangeResponse(200, corruptBytes.size.toLong(), ByteArrayInputStream(corruptBytes))
                },
                usableSpaceProvider = { 512L * 1024L * 1024L }
            )

            try {
                installer.downloadAndInstall(item, stagingDir, modelsDir) {}
                fail("Expected PermanentDownloadException on SHA-256 mismatch")
            } catch (e: PermanentDownloadException) {
                assertTrue(e.message.orEmpty().contains("Checksum verification failed"))
            }

            assertFalse("Corrupted model must not be installed", File(modelsDir, "models-corrupt").exists())
            assertFalse(
                "Corrupted .part file must be deleted",
                File(stagingDir, "models-corrupt/x4.bin.part").exists()
            )
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun downloaderFailsFastWhenFreeDiskSpaceIsInsufficient() = runTest {
        val tempRoot = Files.createTempDirectory("realscale_dl_nospace").toFile()
        try {
            val payload = ByteArray(1024)
            val item = ModelCatalogItem(
                id = "models-nospace",
                displayName = "No Space",
                description = "Free space test",
                category = "PHOTO",
                engineTool = "realsr-ncnn",
                defaultScale = 4,
                supportedScales = listOf(4),
                totalBytes = 1024L,
                files = listOf(
                    ModelFileSpec(
                        fileName = "x4.bin",
                        url = "https://huggingface.co/tumuyan2/realsr-models/resolve/main/x4.bin",
                        sizeBytes = 1024L,
                        sha256 = sha256(payload)
                    )
                )
            )

            var networkCalled = false
            val installer = ResumableModelInstaller(
                httpClient = { _, _ ->
                    networkCalled = true
                    HttpRangeResponse(200, 1024L, ByteArrayInputStream(payload))
                },
                usableSpaceProvider = { 1024L } // Less than 16 MiB headroom
            )

            try {
                installer.downloadAndInstall(item, File(tempRoot, "s"), File(tempRoot, "m")) {}
                fail("Expected PermanentDownloadException when free space is too low")
            } catch (e: PermanentDownloadException) {
                assertTrue(e.message.orEmpty().contains("Not enough storage space"))
            }
            assertFalse("Should fail before opening network connection", networkCalled)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { String.format(Locale.US, "%02x", it) }
}
