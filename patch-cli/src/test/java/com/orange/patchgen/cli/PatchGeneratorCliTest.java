package com.orange.patchgen.cli;

import com.orange.patchgen.config.EngineType;
import com.orange.patchgen.config.PatchMode;
import com.orange.patchgen.config.SigningConfig;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CLI 集成测试
 * 
 * 测试基本生成流程和各种参数组合
 * Requirements: 8.9, 8.10, 8.11
 */
public class PatchGeneratorCliTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    private File baseApk;
    private File newApk;
    private File outputPatch;

    private Method buildOptionsMethod;
    private Method validateRequiredParamsMethod;
    private Method validateInputFilesMethod;
    private Method parseEngineTypeMethod;
    private Method parsePatchModeMethod;
    private Method buildSigningConfigMethod;

    @Before
    public void setUp() throws Exception {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));

        baseApk = tempFolder.newFile("base.apk");
        newApk = tempFolder.newFile("new.apk");
        outputPatch = new File(tempFolder.getRoot(), "output.patch");

        createMinimalApk(baseApk, "1.0.0", 1);
        createMinimalApk(newApk, "1.0.1", 2);

        buildOptionsMethod = PatchGeneratorCli.class.getDeclaredMethod("buildOptions");
        buildOptionsMethod.setAccessible(true);

        validateRequiredParamsMethod = PatchGeneratorCli.class.getDeclaredMethod(
            "validateRequiredParams", CommandLine.class);
        validateRequiredParamsMethod.setAccessible(true);

        validateInputFilesMethod = PatchGeneratorCli.class.getDeclaredMethod(
            "validateInputFiles", File.class, File.class);
        validateInputFilesMethod.setAccessible(true);

        parseEngineTypeMethod = PatchGeneratorCli.class.getDeclaredMethod(
            "parseEngineType", String.class);
        parseEngineTypeMethod.setAccessible(true);

        parsePatchModeMethod = PatchGeneratorCli.class.getDeclaredMethod(
            "parsePatchMode", String.class);
        parsePatchModeMethod.setAccessible(true);

        buildSigningConfigMethod = PatchGeneratorCli.class.getDeclaredMethod(
            "buildSigningConfig", CommandLine.class);
        buildSigningConfigMethod.setAccessible(true);
    }

    @After
    public void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    public void testHelpOptionOutput() throws Exception {
        Options options = (Options) buildOptionsMethod.invoke(null);
        
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(pw, 100, "patch-generator", "", options, 2, 2, "", true);
        String helpOutput = sw.toString();
        
        assertThat(helpOutput).contains("--base");
        assertThat(helpOutput).contains("--new");
        assertThat(helpOutput).contains("--output");
        assertThat(helpOutput).contains("--keystore");
        assertThat(helpOutput).contains("--engine");
        assertThat(helpOutput).contains("--mode");
    }

    @Test
    public void testMissingRequiredParams_Base() throws Exception {
        Options options = (Options) buildOptionsMethod.invoke(null);
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{
            "--new", newApk.getAbsolutePath(),
            "--output", outputPatch.getAbsolutePath()
        });
        
        assertThatThrownBy(() -> validateRequiredParamsMethod.invoke(null, cmd))
            .hasRootCauseInstanceOf(ParseException.class)
            .rootCause()
            .hasMessageContaining("--base");
    }

    @Test
    public void testMissingRequiredParams_New() throws Exception {
        Options options = (Options) buildOptionsMethod.invoke(null);
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{
            "--base", baseApk.getAbsolutePath(),
            "--output", outputPatch.getAbsolutePath()
        });
        
        assertThatThrownBy(() -> validateRequiredParamsMethod.invoke(null, cmd))
            .hasRootCauseInstanceOf(ParseException.class)
            .rootCause()
            .hasMessageContaining("--new");
    }

    @Test
    public void testMissingRequiredParams_Output() throws Exception {
        Options options = (Options) buildOptionsMethod.invoke(null);
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{
            "--base", baseApk.getAbsolutePath(),
            "--new", newApk.getAbsolutePath()
        });
        
        assertThatThrownBy(() -> validateRequiredParamsMethod.invoke(null, cmd))
            .hasRootCauseInstanceOf(ParseException.class)
            .rootCause()
            .hasMessageContaining("--output");
    }

    @Test
    public void testAllRequiredParamsProvided() throws Exception {
        Options options = (Options) buildOptionsMethod.invoke(null);
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{
            "--base", baseApk.getAbsolutePath(),
            "--new", newApk.getAbsolutePath(),
            "--output", outputPatch.getAbsolutePath()
        });
        
        validateRequiredParamsMethod.invoke(null, cmd);
    }

    @Test
    public void testBaseApkNotFound() throws Exception {
        File nonExistentFile = new File("/nonexistent/path/base.apk");
        
        assertThatThrownBy(() -> validateInputFilesMethod.invoke(null, nonExistentFile, newApk))
            .hasRootCauseInstanceOf(ParseException.class)
            .rootCause()
            .hasMessageContaining("not found");
    }

    @Test
    public void testNewApkNotFound() throws Exception {
        File nonExistentFile = new File("/nonexistent/path/new.apk");
        
        assertThatThrownBy(() -> validateInputFilesMethod.invoke(null, baseApk, nonExistentFile))
            .hasRootCauseInstanceOf(ParseException.class)
            .rootCause()
            .hasMessageContaining("not found");
    }

    @Test
    public void testValidInputFiles() throws Exception {
        validateInputFilesMethod.invoke(null, baseApk, newApk);
    }

    @Test
    public void testInvalidEngineType() {
        assertThatThrownBy(() -> parseEngineTypeMethod.invoke(null, "invalid_engine"))
            .hasRootCauseInstanceOf(ParseException.class)
            .rootCause()
            .hasMessageContaining("Invalid engine type");
    }

    @Test
    public void testValidEngineType_Auto() throws Exception {
        EngineType result = (EngineType) parseEngineTypeMethod.invoke(null, "auto");
        assertThat(result).isEqualTo(EngineType.AUTO);
    }

    @Test
    public void testValidEngineType_Java() throws Exception {
        EngineType result = (EngineType) parseEngineTypeMethod.invoke(null, "java");
        assertThat(result).isEqualTo(EngineType.JAVA);
    }

    @Test
    public void testValidEngineType_Native() throws Exception {
        EngineType result = (EngineType) parseEngineTypeMethod.invoke(null, "native");
        assertThat(result).isEqualTo(EngineType.NATIVE);
    }

    @Test
    public void testInvalidPatchMode() {
        assertThatThrownBy(() -> parsePatchModeMethod.invoke(null, "invalid_mode"))
            .hasRootCauseInstanceOf(ParseException.class)
            .rootCause()
            .hasMessageContaining("Invalid patch mode");
    }

    @Test
    public void testValidPatchMode_FullDex() throws Exception {
        PatchMode result = (PatchMode) parsePatchModeMethod.invoke(null, "full_dex");
        assertThat(result).isEqualTo(PatchMode.FULL_DEX);
    }

    @Test
    public void testValidPatchMode_FullDexAlternative() throws Exception {
        PatchMode result = (PatchMode) parsePatchModeMethod.invoke(null, "fulldex");
        assertThat(result).isEqualTo(PatchMode.FULL_DEX);
    }

    @Test
    public void testValidPatchMode_BsDiff() throws Exception {
        PatchMode result = (PatchMode) parsePatchModeMethod.invoke(null, "bsdiff");
        assertThat(result).isEqualTo(PatchMode.BSDIFF);
    }

    @Test
    public void testShortParameterFormat() throws Exception {
        Options options = (Options) buildOptionsMethod.invoke(null);
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{
            "-b", baseApk.getAbsolutePath(),
            "-n", newApk.getAbsolutePath(),
            "-o", outputPatch.getAbsolutePath(),
            "-e", "java",
            "-m", "full_dex",
            "-v"
        });
        
        assertThat(cmd.getOptionValue("base")).isEqualTo(baseApk.getAbsolutePath());
        assertThat(cmd.getOptionValue("new")).isEqualTo(newApk.getAbsolutePath());
        assertThat(cmd.getOptionValue("output")).isEqualTo(outputPatch.getAbsolutePath());
        assertThat(cmd.getOptionValue("engine")).isEqualTo("java");
        assertThat(cmd.getOptionValue("mode")).isEqualTo("full_dex");
        assertThat(cmd.hasOption("verbose")).isTrue();
    }

    @Test
    public void testLongParameterFormat() throws Exception {
        Options options = (Options) buildOptionsMethod.invoke(null);
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{
            "--base", baseApk.getAbsolutePath(),
            "--new", newApk.getAbsolutePath(),
            "--output", outputPatch.getAbsolutePath(),
            "--engine", "native",
            "--mode", "bsdiff",
            "--verbose"
        });
        
        assertThat(cmd.getOptionValue("base")).isEqualTo(baseApk.getAbsolutePath());
        assertThat(cmd.getOptionValue("new")).isEqualTo(newApk.getAbsolutePath());
        assertThat(cmd.getOptionValue("output")).isEqualTo(outputPatch.getAbsolutePath());
        assertThat(cmd.getOptionValue("engine")).isEqualTo("native");
        assertThat(cmd.getOptionValue("mode")).isEqualTo("bsdiff");
        assertThat(cmd.hasOption("verbose")).isTrue();
    }

    @Test
    public void testIncompleteSigningParams_NoKeystore() throws Exception {
        Options options = (Options) buildOptionsMethod.invoke(null);
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{
            "--base", baseApk.getAbsolutePath(),
            "--new", newApk.getAbsolutePath(),
            "--output", outputPatch.getAbsolutePath()
        });
        
        SigningConfig result = (SigningConfig) buildSigningConfigMethod.invoke(null, cmd);
        assertThat(result).isNull();
    }

    @Test
    public void testIncompleteSigningParams_MissingKeyAlias() throws Exception {
        File keystoreFile = tempFolder.newFile("test.jks");
        
        Options options = (Options) buildOptionsMethod.invoke(null);
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{
            "--base", baseApk.getAbsolutePath(),
            "--new", newApk.getAbsolutePath(),
            "--output", outputPatch.getAbsolutePath(),
            "--keystore", keystoreFile.getAbsolutePath()
        });
        
        SigningConfig result = (SigningConfig) buildSigningConfigMethod.invoke(null, cmd);
        assertThat(result).isNull();
        
        String errOutput = errContent.toString();
        assertThat(errOutput).contains("Warning");
    }

    @Test
    public void testCompleteSigningParams() throws Exception {
        File keystoreFile = tempFolder.newFile("test.jks");
        
        Options options = (Options) buildOptionsMethod.invoke(null);
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{
            "--base", baseApk.getAbsolutePath(),
            "--new", newApk.getAbsolutePath(),
            "--output", outputPatch.getAbsolutePath(),
            "--keystore", keystoreFile.getAbsolutePath(),
            "--key-alias", "mykey",
            "--key-password", "secret"
        });
        
        SigningConfig result = (SigningConfig) buildSigningConfigMethod.invoke(null, cmd);
        assertThat(result).isNotNull();
    }

    @Test
    public void testHelpOptionExists() throws Exception {
        Options options = (Options) buildOptionsMethod.invoke(null);
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{"--help"});
        
        assertThat(cmd.hasOption("help")).isTrue();
    }

    @Test
    public void testVersionOptionExists() throws Exception {
        Options options = (Options) buildOptionsMethod.invoke(null);
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{"--version"});
        
        assertThat(cmd.hasOption("version")).isTrue();
    }

    @Test
    public void testDefaultEngineType() throws Exception {
        Options options = (Options) buildOptionsMethod.invoke(null);
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{
            "--base", baseApk.getAbsolutePath(),
            "--new", newApk.getAbsolutePath(),
            "--output", outputPatch.getAbsolutePath()
        });
        
        String engineValue = cmd.getOptionValue("engine", "auto");
        EngineType result = (EngineType) parseEngineTypeMethod.invoke(null, engineValue);
        assertThat(result).isEqualTo(EngineType.AUTO);
    }

    @Test
    public void testDefaultPatchMode() throws Exception {
        Options options = (Options) buildOptionsMethod.invoke(null);
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, new String[]{
            "--base", baseApk.getAbsolutePath(),
            "--new", newApk.getAbsolutePath(),
            "--output", outputPatch.getAbsolutePath()
        });
        
        String modeValue = cmd.getOptionValue("mode", "full_dex");
        PatchMode result = (PatchMode) parsePatchModeMethod.invoke(null, modeValue);
        assertThat(result).isEqualTo(PatchMode.FULL_DEX);
    }

    private void createMinimalApk(File apkFile, String versionName, int versionCode) 
            throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(apkFile))) {
            ZipEntry manifestEntry = new ZipEntry("AndroidManifest.xml");
            zos.putNextEntry(manifestEntry);
            byte[] manifestData = createMinimalBinaryManifest(versionName, versionCode);
            zos.write(manifestData);
            zos.closeEntry();

            ZipEntry dexEntry = new ZipEntry("classes.dex");
            zos.putNextEntry(dexEntry);
            byte[] dexData = createMinimalDex();
            zos.write(dexData);
            zos.closeEntry();
        }
    }

    private byte[] createMinimalBinaryManifest(String versionName, int versionCode) {
        return ("<?xml version=\"1.0\"?><manifest package=\"com.test\" " +
                "versionCode=\"" + versionCode + "\" versionName=\"" + versionName + "\"/>")
                .getBytes();
    }

    private byte[] createMinimalDex() {
        byte[] dex = new byte[112];
        dex[0] = 0x64;
        dex[1] = 0x65;
        dex[2] = 0x78;
        dex[3] = 0x0a;
        dex[4] = 0x30;
        dex[5] = 0x33;
        dex[6] = 0x35;
        dex[7] = 0x00;
        return dex;
    }
}
