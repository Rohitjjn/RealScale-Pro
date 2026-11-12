package dev.realscale.pro.core.engine

/** Pure builder for upstream NCNN CLI invocations (mirrors upstream CommandListManager presets). */
object NcnnCommandBuilder {
    fun upscale(binary: String, input: String, output: String, modelDir: String, scale: Int): List<String> =
        listOf("./$binary", "-i", input, "-o", output, "-m", modelDir, "-s", scale.toString())

    fun classicalResize(input: String, output: String, scale: Int, method: String = "lanczos"): List<String> =
        listOf("./resize-ncnn", "-i", input, "-o", output, "-m", method, "-s", scale.toString())

    fun anime4k(input: String, output: String, scale: Int): List<String> =
        listOf("./Anime4k", "-i", input, "-o", output, "-z", scale.toString(), "-A")

    private val pct = Regex("""(\d{1,3}(?:\.\d+)?)\s*%""")
    fun parseProgress(line: String): Float? =
        pct.find(line)?.groupValues?.get(1)?.toFloatOrNull()?.div(100f)?.coerceIn(0f, 1f)
}
