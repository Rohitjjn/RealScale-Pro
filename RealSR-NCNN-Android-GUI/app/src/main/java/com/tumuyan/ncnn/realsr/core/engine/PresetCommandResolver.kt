package com.tumuyan.ncnn.realsr.core.engine

import com.tumuyan.ncnn.realsr.CommandBuilder
import com.tumuyan.ncnn.realsr.CommandListManager
import com.tumuyan.ncnn.realsr.ShellUtils
import java.io.File

/**
 * Pure deterministic translator from RealScale presets / installed models to native CLI commands
 * via upstream's [CommandBuilder] and [ShellUtils].
 */
object PresetCommandResolver {

    private fun safeShellArg(path: String): String {
        val isSimpleSafe = path.isNotEmpty() && path.all { ch ->
            ch.isLetterOrDigit() || ch == '/' || ch == '.' || ch == '_' || ch == '-' || ch == ':'
        }
        return if (isSimpleSafe) path else ShellUtils.escapeShellArgument(path)
    }

    /**
     * Exact-size finishing pass: `resize-ncnn` with the Lanczos (AVIR) filter.
     *
     * Used after a model whose native factor is not the factor the user asked for (3× on a 4× model,
     * 5× anywhere). [factor] may be fractional, e.g. 1.25 for 5× from a 4× model.
     */
    fun buildResizeCommand(
        inputPath: String,
        outputPath: String,
        factor: Double,
    ): String {
        val rounded = kotlin.math.round(factor * 10_000.0) / 10_000.0
        val text = if (rounded == kotlin.math.floor(rounded)) {
            rounded.toInt().toString()
        } else {
            String.format(java.util.Locale.US, "%.4f", rounded).trimEnd('0').trimEnd('.')
        }
        return CommandBuilder()
            .append("./resize-ncnn")
            .append("-i", safeShellArg(inputPath))
            .append("-o", safeShellArg(outputPath))
            .append("-m", "avir-lancir")
            .append("-s", text)
            .build()
    }

    fun buildUpscaleCommand(
        preset: UpscalePreset,
        scale: Int,
        inputPath: String,
        outputPath: String,
        preferences: EnginePreferences,
        modelOverride: ModelExecutionSpec? = null,
        modelsRootDir: File? = null
    ): String {
        val escapedInput = safeShellArg(inputPath)
        val escapedOutput = safeShellArg(outputPath)

        if (preset.id == PresetId.INSTANT && modelOverride == null) {
            return CommandBuilder()
                .append("./resize-ncnn")
                .append("-i", escapedInput)
                .append("-o", escapedOutput)
                .append("-m", "avir-lancir")
                .append("-s", scale)
                .build()
        }

        val tool = modelOverride?.engineTool ?: preset.engineTool
        // The binary gets the factor its weights can run at; the engine resizes afterwards.
        val effectiveScale = modelOverride?.modelScale ?: scale
        val denoise = modelOverride?.denoiseLevel ?: preset.denoiseLevel
        val modelPath = modelOverride?.modelPath ?: run {
            requireNotNull(modelsRootDir) { "modelsRootDir required when modelOverride is null" }
            File(modelsRootDir, preset.defaultModelId).absolutePath
        }
        val escapedModelPath = safeShellArg(modelPath)

        return when (tool) {
            "resize-ncnn" -> {
                CommandBuilder()
                    .append("./resize-ncnn")
                    .append("-i", escapedInput)
                    .append("-o", escapedOutput)
                    .append("-m", "avir-lancir")
                    .append("-s", effectiveScale)
                    .build()
            }

            "Anime4k" -> {
                val builder = CommandBuilder()
                    .append("./Anime4k")
                    .append("-i", escapedInput)
                    .append("-o", escapedOutput)
                    .append("-z", effectiveScale)
                    .append("-A")
                if (preferences.backend == ExecutionBackend.AUTO_GPU) {
                    builder.append("-w")
                }
                builder.append("-E", ".${preferences.outputFormat.extension}")
                builder.build()
            }

            "mnnsr-ncnn" -> {
                val builder = CommandBuilder()
                    .append("./mnnsr-ncnn")
                    .append("-i", escapedInput)
                    .append("-o", escapedOutput)
                    .append("-m", escapedModelPath)
                    .append("-s", effectiveScale)
                if (preferences.tileSize >= 32) {
                    builder.append("-t", preferences.tileSize)
                }
                val backendFlag = if (preferences.backend == ExecutionBackend.CPU) 0 else 3
                builder.append("-b", backendFlag)
                builder.append("-f", preferences.outputFormat.cliFormat)
                builder.build()
            }

            else -> {
                // realsr-ncnn, realcugan-ncnn, waifu2x-ncnn, srmd-ncnn
                val builder = CommandBuilder()
                    .append("./$tool")
                    .append("-i", escapedInput)
                    .append("-o", escapedOutput)
                    .append("-m", escapedModelPath)
                    .append("-s", effectiveScale)

                if (denoise != null && (tool == "waifu2x-ncnn" || tool == "realcugan-ncnn" || tool == "srmd-ncnn")) {
                    builder.append("-n", denoise)
                }
                if (preferences.tileSize >= 32) {
                    builder.append("-t", preferences.tileSize)
                }
                if (preferences.threadCount.isNotBlank()) {
                    builder.append("-j", preferences.threadCount.trim())
                }
                // "Enhanced quality (TTA)": all four ncnn tools accept upstream's `-x`, which runs
                // the network 8 ways and averages. mnnsr-ncnn has no such flag (it is commented out
                // in MNN-SR/main.cpp), so the MNN branch above deliberately ignores it.
                if (preferences.tta) {
                    builder.append("-x")
                }
                // srmd-ncnn is Vulkan GPU-only in upstream main.cpp; others accept -g -1 for CPU
                if (preferences.backend == ExecutionBackend.CPU && tool != "srmd-ncnn") {
                    builder.append("-g", "-1")
                }
                builder.append("-f", preferences.outputFormat.cliFormat)
                builder.build()
            }
        }
    }

    data class DiscoveredCommand(
        val label: String,
        val command: String
    )

    /**
     * Exposes upstream [CommandListManager] discovery for the Advanced settings console.
     */
    fun discoverAdvancedCommands(
        modelsRootDir: File,
        extraCommandsText: String = "",
        presetLabels: Array<String> = emptyArray(),
        classicalFilters: Array<String> = emptyArray(),
        magickFilters: Array<String> = emptyArray()
    ): List<DiscoveredCommand> {
        val manager = CommandListManager(
            presetLabels,
            modelsRootDir.absolutePath,
            extraCommandsText,
            classicalFilters,
            magickFilters
        )
        val labels = manager.getDisplayLabels(false)
        return (0 until manager.commandCount).map { idx ->
            DiscoveredCommand(
                label = labels.getOrElse(idx) { "Command ${idx + 1}" },
                command = manager.getCommandAt(idx)
            )
        }
    }
}
