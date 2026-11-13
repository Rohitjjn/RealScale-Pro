package com.tumuyan.ncnn.realsr;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Golden parity tests locking in upstream 1.13.2 engine command generation across all 8 CLI tools:
 * realsr-ncnn, realcugan-ncnn, waifu2x-ncnn, srmd-ncnn, mnnsr-ncnn, resize-ncnn, Anime4k, magick.
 */
public class GoldenEngineCommandTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Map<String, String> loadGoldenFixtures() throws IOException {
        Map<String, String> map = new HashMap<>();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("golden_commands.txt")) {
            assertNotNull("golden_commands.txt must be on test classpath", is);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    assertTrue("Invalid fixture line: " + line, eq > 0);
                    map.put(line.substring(0, eq), line.substring(eq + 1));
                }
            }
        }
        return map;
    }

    @Test
    public void builtinAndDynamicCommandsMatchGoldenFixtures() throws IOException {
        Map<String, String> golden = loadGoldenFixtures();

        File extraRoot = tempFolder.newFolder("models");
        createModelFile(extraRoot, "models-DF2K", "x4.bin");
        createModelFile(extraRoot, "models-cunet", "noise0_scale2.0x_model.bin");
        createModelFile(extraRoot, "models-cunet", "scale2.0x_model.bin");
        createModelFile(extraRoot, "models-upconv_7_photo", "noise1_scale2.0x_model.bin");
        createModelFile(extraRoot, "models-srmd", "srmd_x2.bin");
        createModelFile(extraRoot, "models-srmd", "srmd_x3.bin");
        createModelFile(extraRoot, "models-srmd", "srmd_x4.bin");
        createModelFile(extraRoot, "models-MNN", "RealESRGAN_x4plus_anime_6B-x4.mnn");

        String[] classicalFilters = new String[]{"nearest", "bilinear", "bicubic", "avir", "avir-lancir"};
        String[] magickFilters = new String[]{"Lanczos2", "Mitchell"};
        String[] presetLabels = new String[CommandListManager.COMMAND_0.length];
        Arrays.fill(presetLabels, "preset");

        CommandListManager manager = new CommandListManager(
                presetLabels,
                extraRoot.getAbsolutePath(),
                "",
                classicalFilters,
                magickFilters
        );

        assertEquals(golden.get("BUILTIN_0"), manager.getCommandAt(0));
        assertEquals(golden.get("BUILTIN_1"), manager.getCommandAt(1));
        assertEquals(golden.get("BUILTIN_2"), manager.getCommandAt(2));
        assertEquals(golden.get("BUILTIN_3"), manager.getCommandAt(3));
        assertEquals(golden.get("BUILTIN_4"), manager.getCommandAt(4));
        assertEquals(golden.get("BUILTIN_5"), manager.getCommandAt(5));
        assertEquals(golden.get("BUILTIN_8"), manager.getCommandAt(8));
        assertEquals(golden.get("BUILTIN_9"), manager.getCommandAt(9));
        assertEquals(golden.get("BUILTIN_10"), manager.getCommandAt(10));
        assertEquals(golden.get("BUILTIN_13"), manager.getCommandAt(13));
        assertEquals(golden.get("BUILTIN_14"), manager.getCommandAt(14));
        assertEquals(golden.get("BUILTIN_21"), manager.getCommandAt(21));
        assertEquals(golden.get("BUILTIN_22"), manager.getCommandAt(22));
        assertEquals(golden.get("BUILTIN_27"), manager.getCommandAt(27));
        assertEquals(golden.get("BUILTIN_28"), manager.getCommandAt(28));
        assertEquals(golden.get("BUILTIN_32"), manager.getCommandAt(32));
        assertEquals(golden.get("BUILTIN_37"), manager.getCommandAt(37));

        List<String> all = Arrays.asList(manager.commandList);
        assertTrue(all.contains(golden.get("CLASSICAL_NEAREST_2")));
        assertTrue(all.contains(golden.get("CLASSICAL_BICUBIC_4")));
        assertTrue(all.contains(golden.get("CLASSICAL_LANCIR_2")));
        assertTrue(all.contains(golden.get("CLASSICAL_LANCIR_4")));
        assertTrue(all.contains(golden.get("MAGICK_LANCZOS2_2")));
        assertTrue(all.contains(golden.get("MAGICK_MITCHELL_4")));

        String rootPath = extraRoot.getAbsolutePath();
        assertTrue(all.contains(golden.get("EXTRA_DF2K").replace("<ROOT>", rootPath)));
        assertTrue(all.contains(golden.get("EXTRA_CUNET_N0").replace("<ROOT>", rootPath)));
        assertTrue(all.contains(golden.get("EXTRA_CUNET_PLAIN").replace("<ROOT>", rootPath)));
        assertTrue(all.contains(golden.get("EXTRA_UPCONV_PHOTO_N1").replace("<ROOT>", rootPath)));
        assertTrue(all.contains(golden.get("EXTRA_SRMD_2").replace("<ROOT>", rootPath)));
        assertTrue(all.contains(golden.get("EXTRA_SRMD_3").replace("<ROOT>", rootPath)));
        assertTrue(all.contains(golden.get("EXTRA_SRMD_4").replace("<ROOT>", rootPath)));
        assertTrue(all.contains(golden.get("EXTRA_MNN_ANIME6B").replace("<ROOT>", rootPath)));
    }

    @Test
    public void commandBuilderAndShellEscapingMatchGoldenFixtures() throws IOException {
        Map<String, String> golden = loadGoldenFixtures();

        CommandBuilder singleSave = new CommandBuilder()
                .append("./realsr-ncnn -i input.png -o output.png  -m models-Real-ESRGANv3-general -s 4")
                .append("-t", 64)
                .append("-j", "1:2:2")
                .appendIf(true, "-g -1")
                .append(";cp /data/user/0/app/cache/realsr/output.png "
                        + ShellUtils.escapeShellArgument("/storage/emulated/0/DCIM/RealSR/photo_0101_120000.png"));
        assertEquals(golden.get("BUILDER_SINGLE_AUTOSAVE"), singleSave.build());

        CommandBuilder gifSave = new CommandBuilder()
                .append("./realsr-ncnn -i input.png -o output.png  -m models-Real-ESRGAN-anime")
                .append(";./magick -delay 8 output.png/* -loop 0 "
                        + ShellUtils.escapeShellArgument("/storage/emulated/0/DCIM/RealSR/anim_0101_120000.gif"));
        assertEquals(golden.get("BUILDER_GIF_REASSEMBLE"), gifSave.build());

        assertEquals(golden.get("SHELL_ESCAPE_INJECTION"), ShellUtils.escapeShellArgument("'; rm -rf /; '"));
    }

    @Test
    public void programFilteringAndDirectoryModeMatchUpstreamRules() {
        String[] presetLabels = new String[CommandListManager.COMMAND_0.length];
        Arrays.fill(presetLabels, "preset");
        CommandListManager manager = new CommandListManager(
                presetLabels,
                "",
                "",
                new String[]{"avir-lancir"},
                new String[]{"Lanczos2"}
        );

        assertTrue(CommandListManager.supportsDirectoryMode("./realsr-ncnn -i input.png -o output.png  -m models-DF2K -s 4"));
        assertTrue(CommandListManager.supportsDirectoryMode("./realcugan-ncnn -i input.png -o output.png  -m models-se -s 2  -n 0"));
        assertTrue(CommandListManager.supportsDirectoryMode("./waifu2x-ncnn -i input.png -o output.png  -m models-cunet -s 2"));
        assertTrue(CommandListManager.supportsDirectoryMode("./srmd-ncnn -i input.png -o output.png  -m models-srmd -s 2"));
        assertTrue(CommandListManager.supportsDirectoryMode("./mnnsr-ncnn -i input.png -o output.png  -m models-MNN/foo.mnn -s 4"));
        assertTrue(CommandListManager.supportsDirectoryMode("./resize-ncnn -i input.png -o output.png  -m avir-lancir -s 2"));
        assertTrue(CommandListManager.supportsDirectoryMode("./Anime4k -i input.png -o output.png -z 2 -A"));
        assertFalse(CommandListManager.supportsDirectoryMode("./magick input.png -filter Lanczos2 -resize 200% output.png "));

        Set<String> hidden = new HashSet<>(Collections.singletonList(CommandListManager.PROGRAM_MAGICK));
        for (String cmd : manager.getFilteredCommands(hidden)) {
            assertFalse(cmd.startsWith("./magick "));
        }

        assertTrue(ProgressLogHelper.isProgressLine("50.00%"));
        assertTrue(ProgressLogHelper.isProgressLine("  73.25%     elpsed:    12.30s"));
        assertFalse(ProgressLogHelper.isProgressLine("vkQueueSubmit failed -4"));
    }

    private void createModelFile(File root, String dirName, String fileName) throws IOException {
        File dir = new File(root, dirName);
        if (!dir.exists()) {
            assertTrue(dir.mkdirs());
        }
        File file = new File(dir, fileName);
        assertTrue(file.createNewFile());
    }
}
