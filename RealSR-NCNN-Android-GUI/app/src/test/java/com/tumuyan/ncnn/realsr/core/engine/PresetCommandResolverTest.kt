package com.tumuyan.ncnn.realsr.core.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class PresetCommandResolverTest {

    @Test
    fun instantPresetUsesZeroDownloadResizeNcnnWithoutModelFiles() {
        val cmd2x = PresetCommandResolver.buildUpscaleCommand(
            preset = UpscalePreset.INSTANT,
            scale = 2,
            inputPath = "/data/user/0/app/files/in.png",
            outputPath = "/data/user/0/app/files/out.png",
            preferences = EnginePreferences()
        )
        assertEquals(
            "./resize-ncnn -i /data/user/0/app/files/in.png -o /data/user/0/app/files/out.png -m avir-lancir -s 2",
            cmd2x
        )

        val cmd4x = PresetCommandResolver.buildUpscaleCommand(
            preset = UpscalePreset.INSTANT,
            scale = 4,
            inputPath = "/data/user/0/app/files/my photo.jpg",
            outputPath = "/data/user/0/app/files/out.webp",
            preferences = EnginePreferences(outputFormat = OutputFormat.WEBP)
        )
        assertEquals(
            "./resize-ncnn -i '/data/user/0/app/files/my photo.jpg' -o /data/user/0/app/files/out.webp -m avir-lancir -s 4",
            cmd4x
        )
    }

    @Test
    fun photoAnimeAndMaximumPresetsMapToExpectedNativeCommands() {
        val modelsRoot = File("/data/user/0/app/files/models")
        val gpuPrefs = EnginePreferences(
            backend = ExecutionBackend.AUTO_GPU,
            tileSize = 128,
            threadCount = "2:2:2",
            outputFormat = OutputFormat.PNG
        )

        assertEquals(
            "./realsr-ncnn -i /tmp/in.png -o /tmp/out.png -m /data/user/0/app/files/models/models-RealeSR-general-v3 -s 4 -t 128 -j 2:2:2 -f png",
            PresetCommandResolver.buildUpscaleCommand(
                preset = UpscalePreset.PHOTO,
                scale = 4,
                inputPath = "/tmp/in.png",
                outputPath = "/tmp/out.png",
                preferences = gpuPrefs,
                modelsRootDir = modelsRoot
            )
        )

        assertEquals(
            "./waifu2x-ncnn -i /tmp/in.png -o /tmp/out.png -m /data/user/0/app/files/models/models-upconv_7_anime_style_art_rgb -s 2 -n 2 -t 128 -j 2:2:2 -f png",
            PresetCommandResolver.buildUpscaleCommand(
                preset = UpscalePreset.ANIME,
                scale = 2,
                inputPath = "/tmp/in.png",
                outputPath = "/tmp/out.png",
                preferences = gpuPrefs,
                modelsRootDir = modelsRoot
            )
        )

        val cpuPrefs = EnginePreferences(
            backend = ExecutionBackend.CPU,
            tileSize = 64,
            outputFormat = OutputFormat.WEBP
        )
        assertEquals(
            "./realsr-ncnn -i /tmp/in.png -o /tmp/out.webp -m /data/user/0/app/files/models/models-DF2K -s 4 -t 64 -g -1 -f webp",
            PresetCommandResolver.buildUpscaleCommand(
                preset = UpscalePreset.MAXIMUM,
                scale = 4,
                inputPath = "/tmp/in.png",
                outputPath = "/tmp/out.webp",
                preferences = cpuPrefs,
                modelsRootDir = modelsRoot
            )
        )
    }

    @Test
    fun modelOverrideSupportsMnnSrmdAndAnime4kCorrectly() {
        val cpuPrefs = EnginePreferences(backend = ExecutionBackend.CPU, outputFormat = OutputFormat.JPG)

        // srmd-ncnn is GPU-only in upstream main.cpp so -g -1 must never be appended
        assertEquals(
            "./srmd-ncnn -i /tmp/in.png -o /tmp/out.jpg -m /models/models-srmd -s 4 -n 3 -f jpg",
            PresetCommandResolver.buildUpscaleCommand(
                preset = UpscalePreset.PHOTO,
                scale = 4,
                inputPath = "/tmp/in.png",
                outputPath = "/tmp/out.jpg",
                preferences = cpuPrefs,
                modelOverride = ModelExecutionSpec(
                    modelId = "models-srmd",
                    displayName = "SRMD",
                    engineTool = "srmd-ncnn",
                    modelPath = "/models/models-srmd",
                    scale = 4,
                    denoiseLevel = 3
                )
            )
        )

        assertEquals(
            "./mnnsr-ncnn -i /tmp/in.png -o /tmp/out.jpg -m /models/models-MNNSR/RealESRGAN_x4plus_anime_6B-x4.mnn -s 4 -b 0 -f jpg",
            PresetCommandResolver.buildUpscaleCommand(
                preset = UpscalePreset.ANIME,
                scale = 4,
                inputPath = "/tmp/in.png",
                outputPath = "/tmp/out.jpg",
                preferences = cpuPrefs,
                modelOverride = ModelExecutionSpec(
                    modelId = "models-MNN-RealESRGAN_x4plus_anime_6B",
                    displayName = "MNN Anime 6B",
                    engineTool = "mnnsr-ncnn",
                    modelPath = "/models/models-MNNSR/RealESRGAN_x4plus_anime_6B-x4.mnn",
                    scale = 4
                )
            )
        )
    }

    @Test
    fun tarXzExtractorRestoresExactBinaryBytes() {
        val destDir = Files.createTempDirectory("realscale_tarxz_test").toFile()
        try {
            val expectedA = ByteArray(1500) { idx -> (idx * 31 and 0xFF).toByte() }
            val expectedB = "<?xml version=\"1.0\"?><colormap/>".toByteArray(StandardCharsets.UTF_8)

            val tarXzBytes = buildMinimalTarXz(
                mapOf(
                    "resize-ncnn" to expectedA,
                    "sub/colors.xml" to expectedB
                )
            )

            EngineAssetExtractor.unpackTarXz(ByteArrayInputStream(tarXzBytes), destDir)

            val outA = File(destDir, "resize-ncnn").readBytes()
            val outB = File(destDir, "sub/colors.xml").readBytes()
            org.junit.Assert.assertArrayEquals(expectedA, outA)
            org.junit.Assert.assertArrayEquals(expectedB, outB)
        } finally {
            destDir.deleteRecursively()
        }
    }

    private fun buildMinimalTarXz(entries: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        XZOutputStream(baos, LZMA2Options(3)).use { xz ->
            for ((name, data) in entries) {
                val header = ByteArray(512)
                val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
                System.arraycopy(nameBytes, 0, header, 0, nameBytes.size)
                writeOctal(header, 100, 8, 493L) // 0755
                writeOctal(header, 108, 8, 0L)
                writeOctal(header, 116, 8, 0L)
                writeOctal(header, 124, 12, data.size.toLong())
                writeOctal(header, 136, 12, 1_700_000_000L)
                for (i in 148 until 156) header[i] = ' '.code.toByte()
                header[156] = '0'.code.toByte()
                val magic = "ustar\u000000".toByteArray(StandardCharsets.US_ASCII)
                System.arraycopy(magic, 0, header, 257, magic.size)
                val chksum = header.sumOf { it.toInt() and 0xFF }.toLong()
                writeOctal(header, 148, 7, chksum)
                header[155] = ' '.code.toByte()

                xz.write(header)
                xz.write(data)
                val rem = data.size % 512
                if (rem != 0) {
                    xz.write(ByteArray(512 - rem))
                }
            }
            xz.write(ByteArray(1024))
        }
        return baos.toByteArray()
    }

    private fun writeOctal(buf: ByteArray, offset: Int, len: Int, value: Long) {
        val oct = value.toString(8).padStart(len - 1, '0')
        val bytes = oct.toByteArray(StandardCharsets.US_ASCII)
        System.arraycopy(bytes, 0, buf, offset, bytes.size)
        buf[offset + len - 1] = 0
    }
}
