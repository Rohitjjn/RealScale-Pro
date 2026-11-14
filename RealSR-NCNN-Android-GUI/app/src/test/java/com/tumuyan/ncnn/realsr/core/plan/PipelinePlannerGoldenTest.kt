package com.tumuyan.ncnn.realsr.core.plan

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * Proves the Kotlin planner decides exactly like the prototype: the golden file is produced by
 * running the prototype's own `makePlan` (see `tools/plan-vectors/gen.mjs`), not by a second
 * hand-written implementation.
 */
class PipelinePlannerGoldenTest {

    private val root: JsonObject = Json.parseToJsonElement(
        javaClass.classLoader!!.getResourceAsStream("plan-golden.json")!!.bufferedReader().readText(),
    ).jsonObject

    @Test
    fun recommendedIdsMatchTheCatalogRules() {
        val expected = mapOf(
            "upscale:photo" to "esrgan-general-v3",
            "upscale:anime" to "waifu2x-anime",
            "super:photo" to "esrgan-remacri",
            "super:anime" to "esrgan-anime-sharp",
        )
        root.getValue("recommended").jsonObject.forEach { (key, value) ->
            val (mode, type) = key.split(':')
            val modeId = if (mode == "upscale") ModeId.UPSCALE else ModeId.SUPER
            val imageType = if (type == "photo") ImageType.Photo else ImageType.Anime
            assertEquals("recommended($key)", expected.getValue(key), PipelinePlanner.recommendedId(modeId, imageType))
            assertEquals("golden($key)", value.jsonPrimitive.content, PipelinePlanner.recommendedId(modeId, imageType))
        }
    }

    @Test
    fun plansMatchThePrototypeExactly() {
        val plans = root.getValue("plans").jsonArray
        require(plans.size > 900) { "golden file looks truncated: ${plans.size} plans" }

        var index = 0
        for (element in plans) {
            val case = element.jsonObject
            val input = case.getValue("input").jsonObject
            val photo = input.getValue("photo").jsonObject
            val prefs = input.getValue("prefs").jsonObject
            val opts = input.getValue("opts").jsonObject
            val expected = case.getValue("expected").jsonObject

            val plan = PipelinePlanner.makePlan(
                photo = PlanInput(
                    width = photo.getValue("w").jsonPrimitive.int,
                    height = photo.getValue("h").jsonPrimitive.int,
                    sizeMB = photo.getValue("sizeMB").jsonPrimitive.content.toDouble(),
                    ext = photo.getValue("ext").jsonPrimitive.content,
                    type = if (photo.getValue("type").jsonPrimitive.content == "photo") ImageType.Photo else ImageType.Anime,
                ),
                mode = if (input.getValue("mode").jsonPrimitive.content == "upscale") ModeId.UPSCALE else ModeId.SUPER,
                prefs = prefsOf(prefs),
                installed = input.getValue("installedIds").jsonArray.map { it.jsonPrimitive.content },
                device = PlanDevice.Reference,
                large = opts["large"]?.jsonPrimitive?.content ?: "optimize",
                settingsCompute = opts["compute"]?.jsonPrimitive?.content ?: "auto",
                settingsFormat = opts["format"]?.jsonPrimitive?.content ?: "auto",
            )

            val where = "plan[$index] ${input.getValue("installed").jsonPrimitive.content} ${photo.getValue("w").jsonPrimitive.int}x" +
                "${photo.getValue("h").jsonPrimitive.int} ${input.getValue("mode").jsonPrimitive.content}"
            assertEquals("$where level", expected.getValue("level").jsonPrimitive.content, plan.level.id)
            assertEquals("$where type", expected.getValue("type").jsonPrimitive.content, if (plan.type == ImageType.Photo) "photo" else "anime")
            assertEquals("$where scale", expected.getValue("scale").jsonPrimitive.int, plan.scale)
            assertEquals("$where model", expected.getValue("modelId").jsonPrimitive.content, plan.model.id)
            assertEquals("$where isAuto", expected.getValue("isAuto").jsonPrimitive.boolean, plan.isAuto)
            val expectedWanted = expected["wantedId"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonPrimitive?.content
            assertEquals("$where wanted", expectedWanted, plan.wanted?.id)
            assertEquals("$where tilePx", expected.getValue("tilePx").jsonPrimitive.int, plan.tilePx)
            assertEquals("$where compute", expected.getValue("compute").jsonPrimitive.content, plan.compute)
            assertEquals("$where inW", expected.getValue("inW").jsonPrimitive.int, plan.inW)
            assertEquals("$where inH", expected.getValue("inH").jsonPrimitive.int, plan.inH)
            assertEquals("$where inMP", expected.getValue("inMP").jsonPrimitive.content.toDouble(), plan.inMP, 1e-9)
            assertEquals("$where overLimit", expected.getValue("overLimit").jsonPrimitive.boolean, plan.overLimit)
            assertEquals("$where optimized", expected.getValue("optimized").jsonPrimitive.boolean, plan.optimized)
            assertEquals("$where outW", expected.getValue("outW").jsonPrimitive.int, plan.outW)
            assertEquals("$where outH", expected.getValue("outH").jsonPrimitive.int, plan.outH)
            assertEquals("$where outMP", expected.getValue("outMP").jsonPrimitive.content.toDouble(), plan.outMP, 1e-9)
            assertEquals("$where inputNote", expected.getValue("inputNote").jsonPrimitive.content, plan.inputNote)
            assertEquals("$where format", expected.getValue("format").jsonPrimitive.content, plan.format)
            assertEquals("$where ext", expected.getValue("ext").jsonPrimitive.content, plan.ext)
            assertEquals("$where etaSec", expected.getValue("etaSec").jsonPrimitive.int, plan.etaSec)
            assertEquals("$where sizeMB", expected.getValue("sizeMB").jsonPrimitive.content.toDouble(), plan.sizeMB, 1e-9)
            assertEquals("$where cols", expected.getValue("cols").jsonPrimitive.int, plan.cols)
            assertEquals("$where rows", expected.getValue("rows").jsonPrimitive.int, plan.rows)
            assertEquals("$where tiles", expected.getValue("tiles").jsonPrimitive.int, plan.tiles)
            assertEquals("$where extraResize", expected.getValue("extraResize").jsonPrimitive.boolean, plan.extraResize)
            assertEquals("$where check", checkJson(expected), checkJson(plan))
            index++
        }
    }

    @Test
    fun formattingMatchesThePrototype() {
        val cases = root.getValue("formats").jsonArray
        require(cases.size >= 40) { "golden file looks truncated: ${cases.size} format cases" }
        for (element in cases) {
            val case = element.jsonObject
            val expected = case.getValue("expected").jsonPrimitive.content
            val args = case.getValue("args").jsonArray
            val actual = when (val fn = case.getValue("fn").jsonPrimitive.content) {
                "fmtDims" -> fmtDims(args[0].jsonPrimitive.int, args[1].jsonPrimitive.int)
                "fmtMP" -> fmtMP(args[0].jsonPrimitive.content.toDouble())
                "fmtMB" -> fmtMB(args[0].jsonPrimitive.content.toDouble())
                "fmtEta" -> fmtEta(args[0].jsonPrimitive.content.toDouble().toInt())
                "fmtLeft" -> fmtLeft(args[0].jsonPrimitive.content.toDouble())
                "resultFileName" -> {
                    val at = args[3].jsonArray
                    val calendar = Calendar.getInstance().apply {
                        clear()
                        set(
                            at[0].jsonPrimitive.int, at[1].jsonPrimitive.int, at[2].jsonPrimitive.int,
                            at[3].jsonPrimitive.int, at[4].jsonPrimitive.int,
                        )
                    }
                    resultFileName(args[0].jsonPrimitive.content, args[1].jsonPrimitive.int, args[2].jsonPrimitive.content, calendar)
                }
                else -> error("unknown formatter $fn")
            }
            assertEquals("${case.getValue("fn").jsonPrimitive.content}${args}", expected, actual)
        }
    }

    private fun checkJson(expected: JsonObject): String {
        val check = expected.getValue("check").jsonObject
        val level = check.getValue("level").jsonPrimitive.content
        return when (level) {
            "green" -> "green"
            "red" -> "red:${check.getValue("title").jsonPrimitive.content}:${check.getValue("body").jsonPrimitive.content}:" +
                (check["maxScale"]?.jsonPrimitive?.content ?: "null")
            else -> "amber:${check.getValue("kind").jsonPrimitive.content}:${check.getValue("title").jsonPrimitive.content}:" +
                check.getValue("body").jsonPrimitive.content
        }
    }

    private fun checkJson(plan: Plan): String = when (val check = plan.check) {
        is PlanCheck.Green -> "green"
        is PlanCheck.Red -> "red:${check.title}:${check.body}:${check.maxScale ?: "null"}"
        is PlanCheck.Amber -> "amber:${check.kind}:${check.title}:${check.body}"
    }

    private fun prefsOf(json: JsonObject): JobPrefs {
        fun s(key: String) = json.getValue(key).jsonPrimitive.content
        return JobPrefs(
            level = PlanLevel.entries.first { it.id == s("level") },
            type = s("type"),
            modelId = s("modelId"),
            scale = json.getValue("scale").jsonPrimitive.int,
            tile = TilePreset.entries.first { it.id == s("tile") },
            tileCustom = json.getValue("tileCustom").jsonPrimitive.int,
            overlap = s("overlap"),
            compute = s("compute"),
            threads = json.getValue("threads").jsonPrimitive.int,
            compression = s("compression"),
            compressionQ = json.getValue("compressionQ").jsonPrimitive.int,
            format = OutputFormatPref.entries.first { it.id == s("format") },
            formatQ = json.getValue("formatQ").jsonPrimitive.int,
            denoise = json.getValue("denoise").jsonPrimitive.int,
            tta = json.getValue("tta").jsonPrimitive.boolean,
            precision = s("precision"),
            keepMeta = json.getValue("keepMeta").jsonPrimitive.boolean,
            sharpen = json.getValue("sharpen").jsonPrimitive.int,
            resize = s("resize"),
            color = s("color"),
            cap = s("cap"),
        )
    }
}
