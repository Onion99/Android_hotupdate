package com.orange.patchnative;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * NativePatchEngine 单元测试
 * 
 * 测试 JNI 调用正确性和回调机制
 * 
 * Requirements: 11.3, 11.4, 11.5, 11.6, 11.7
 */
@RunWith(AndroidJUnit4.class)
public class NativePatchEngineTest {
    
    private Context context;
    private File testDir;
    private NativePatchEngine engine;
    
    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        testDir = new File(context.getCacheDir(), "native_patch_test");
        if (!testDir.exists()) {
            testDir.mkdirs();
        }
        
        // 检查 Native 库是否可用
        if (NativePatchEngine.isAvailable()) {
            engine = new NativePatchEngine();
            engine.init();
        }
    }
    
    @After
    public void tearDown() {
        if (engine != null) {
            engine.release();
        }
        // 清理测试文件
        if (testDir != null && testDir.exists()) {
            deleteRecursive(testDir);
        }
    }
    
    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
    
    // ========================================================================
    // Native 库可用性测试
    // ========================================================================
    
    @Test
    public void testNativeLibraryAvailable() {
        // 测试 Native 库是否可以加载
        // 注意：在 Android 设备上运行时，Native 库应该可用
        boolean available = NativePatchEngine.isAvailable();
        assertTrue("Native library should be available on Android device", available);
    }
    
    @Test
    public void testEngineInitialization() {
        // 测试引擎初始化
        // Requirements: 11.2
        assumeNativeAvailable();
        
        NativePatchEngine testEngine = new NativePatchEngine();
        boolean initResult = testEngine.init();
        assertTrue("Engine should initialize successfully", initResult);
        
        assertTrue("Engine should be initialized after init()", testEngine.isInitialized());
        
        testEngine.release();
    }
    
    @Test
    public void testEngineVersion() {
        // 测试获取引擎版本
        assumeNativeAvailable();
        
        String version = engine.getVersion();
        assertNotNull("Version should not be null", version);
        assertFalse("Version should not be empty", version.isEmpty());
    }
    
    // ========================================================================
    // JNI 调用正确性测试 - generateDiff (Requirements: 11.3)
    // ========================================================================
    
    @Test
    public void testGenerateDiff_ValidFiles() throws IOException {
        // 测试生成差异补丁 - 有效文件
        // Requirements: 11.3
        assumeNativeAvailable();
        
        // 创建测试文件
        File oldFile = createTestFile("old.bin", "This is the original content.");
        File newFile = createTestFile("new.bin", "This is the modified content with changes.");
        File patchFile = new File(testDir, "test.patch");
        
        int result = engine.generateDiff(
                oldFile.getAbsolutePath(),
                newFile.getAbsolutePath(),
                patchFile.getAbsolutePath(),
                null
        );
        
        assertEquals("generateDiff should succeed", NativePatchEngine.SUCCESS, result);
        assertTrue("Patch file should be created", patchFile.exists());
        assertTrue("Patch file should have content", patchFile.length() > 0);
    }
    
    @Test
    public void testGenerateDiff_FileNotFound() {
        // 测试生成差异补丁 - 文件不存在
        // Requirements: 11.3
        assumeNativeAvailable();
        
        File nonExistentFile = new File(testDir, "non_existent.bin");
        File newFile = new File(testDir, "new.bin");
        File patchFile = new File(testDir, "test.patch");
        
        int result = engine.generateDiff(
                nonExistentFile.getAbsolutePath(),
                newFile.getAbsolutePath(),
                patchFile.getAbsolutePath(),
                null
        );
        
        assertEquals("generateDiff should return FILE_NOT_FOUND error",
                NativePatchEngine.ERROR_FILE_NOT_FOUND, result);
    }
    
    @Test
    public void testGenerateDiffOrThrow_Success() throws IOException, NativePatchException {
        // 测试生成差异补丁 - 抛出异常版本成功
        // Requirements: 11.3, 11.9
        assumeNativeAvailable();
        
        File oldFile = createTestFile("old2.bin", "Original data here.");
        File newFile = createTestFile("new2.bin", "Modified data here with extra.");
        File patchFile = new File(testDir, "test2.patch");
        
        // 不应抛出异常
        engine.generateDiffOrThrow(
                oldFile.getAbsolutePath(),
                newFile.getAbsolutePath(),
                patchFile.getAbsolutePath()
        );
        
        assertTrue("Patch file should be created", patchFile.exists());
    }
    
    @Test
    public void testGenerateDiffOrThrow_ThrowsException() {
        // 测试生成差异补丁 - 抛出异常版本失败
        // Requirements: 11.3, 11.9
        assumeNativeAvailable();
        
        File nonExistentFile = new File(testDir, "non_existent2.bin");
        File newFile = new File(testDir, "new3.bin");
        File patchFile = new File(testDir, "test3.patch");
        
        try {
            engine.generateDiffOrThrow(
                    nonExistentFile.getAbsolutePath(),
                    newFile.getAbsolutePath(),
                    patchFile.getAbsolutePath()
            );
            fail("Should throw NativePatchException");
        } catch (NativePatchException e) {
            assertEquals("Error code should be FILE_NOT_FOUND",
                    NativePatchEngine.ERROR_FILE_NOT_FOUND, e.getErrorCode());
            assertTrue("isFileNotFound() should return true", e.isFileNotFound());
        }
    }
    
    // ========================================================================
    // JNI 调用正确性测试 - applyPatch (Requirements: 11.4)
    // ========================================================================
    
    @Test
    public void testApplyPatch_ValidPatch() throws IOException {
        // 测试应用补丁 - 有效补丁
        // Requirements: 11.4
        assumeNativeAvailable();
        
        // 先生成补丁
        File oldFile = createTestFile("apply_old.bin", "Original content for apply test.");
        File newFile = createTestFile("apply_new.bin", "Modified content for apply test with changes.");
        File patchFile = new File(testDir, "apply_test.patch");
        
        int genResult = engine.generateDiff(
                oldFile.getAbsolutePath(),
                newFile.getAbsolutePath(),
                patchFile.getAbsolutePath(),
                null
        );
        assertEquals("generateDiff should succeed", NativePatchEngine.SUCCESS, genResult);
        
        // 应用补丁
        File resultFile = new File(testDir, "apply_result.bin");
        int applyResult = engine.applyPatch(
                oldFile.getAbsolutePath(),
                patchFile.getAbsolutePath(),
                resultFile.getAbsolutePath(),
                null
        );
        
        assertEquals("applyPatch should succeed", NativePatchEngine.SUCCESS, applyResult);
        assertTrue("Result file should be created", resultFile.exists());
        assertEquals("Result file size should match new file", newFile.length(), resultFile.length());
    }
    
    @Test
    public void testApplyPatch_InvalidPatch() throws IOException {
        // 测试应用补丁 - 无效补丁
        // Requirements: 11.4
        assumeNativeAvailable();
        
        File oldFile = createTestFile("invalid_old.bin", "Some content.");
        File invalidPatch = createTestFile("invalid.patch", "This is not a valid patch file.");
        File resultFile = new File(testDir, "invalid_result.bin");
        
        int result = engine.applyPatch(
                oldFile.getAbsolutePath(),
                invalidPatch.getAbsolutePath(),
                resultFile.getAbsolutePath(),
                null
        );
        
        // 应该返回错误（可能是 CORRUPT_PATCH 或其他错误）
        assertNotEquals("applyPatch should fail with invalid patch",
                NativePatchEngine.SUCCESS, result);
    }
    
    // ========================================================================
    // JNI 调用正确性测试 - calculateHash (Requirements: 11.5)
    // ========================================================================
    
    @Test
    public void testCalculateMd5_ValidFile() throws IOException {
        // 测试计算 MD5 - 有效文件
        // Requirements: 11.5
        assumeNativeAvailable();
        
        String content = "Test content for MD5 calculation.";
        File testFile = createTestFile("md5_test.txt", content);
        
        String md5 = engine.calculateMd5(testFile.getAbsolutePath());
        
        assertNotNull("MD5 should not be null", md5);
        assertEquals("MD5 should be 32 characters", 32, md5.length());
        assertTrue("MD5 should be hexadecimal", md5.matches("[0-9a-fA-F]+"));
    }
    
    @Test
    public void testCalculateMd5_FileNotFound() {
        // 测试计算 MD5 - 文件不存在
        // Requirements: 11.5
        assumeNativeAvailable();
        
        File nonExistentFile = new File(testDir, "non_existent_md5.txt");
        
        String md5 = engine.calculateMd5(nonExistentFile.getAbsolutePath());
        
        assertNull("MD5 should be null for non-existent file", md5);
    }
    
    @Test
    public void testCalculateMd5_Consistency() throws IOException {
        // 测试计算 MD5 - 一致性
        // Requirements: 11.5
        assumeNativeAvailable();
        
        String content = "Consistent content for MD5 test.";
        File testFile = createTestFile("md5_consistent.txt", content);
        
        String md5First = engine.calculateMd5(testFile.getAbsolutePath());
        String md5Second = engine.calculateMd5(testFile.getAbsolutePath());
        
        assertEquals("MD5 should be consistent for same file", md5First, md5Second);
    }
    
    @Test
    public void testCalculateSha256_ValidFile() throws IOException {
        // 测试计算 SHA256 - 有效文件
        // Requirements: 11.5
        assumeNativeAvailable();
        
        String content = "Test content for SHA256 calculation.";
        File testFile = createTestFile("sha256_test.txt", content);
        
        String sha256 = engine.calculateSha256(testFile.getAbsolutePath());
        
        assertNotNull("SHA256 should not be null", sha256);
        assertEquals("SHA256 should be 64 characters", 64, sha256.length());
        assertTrue("SHA256 should be hexadecimal", sha256.matches("[0-9a-fA-F]+"));
    }
    
    @Test
    public void testCalculateMd5OrThrow_ThrowsException() {
        // 测试计算 MD5 - 抛出异常版本
        // Requirements: 11.5, 11.9
        assumeNativeAvailable();
        
        File nonExistentFile = new File(testDir, "non_existent_md5_throw.txt");
        
        try {
            engine.calculateMd5OrThrow(nonExistentFile.getAbsolutePath());
            fail("Should throw NativePatchException");
        } catch (NativePatchException e) {
            assertEquals("Error code should be HASH_FAILED",
                    NativePatchEngine.ERROR_HASH_FAILED, e.getErrorCode());
        }
    }
    
    // ========================================================================
    // 回调机制测试 (Requirements: 11.6)
    // ========================================================================
    
    @Test
    public void testProgressCallback_GenerateDiff() throws IOException, InterruptedException {
        // 测试进度回调 - 生成差异
        // Requirements: 11.6
        assumeNativeAvailable();
        
        // 创建较大的测试文件以确保有进度回调
        byte[] largeContent = new byte[100 * 1024]; // 100KB
        for (int i = 0; i < largeContent.length; i++) {
            largeContent[i] = (byte) (i % 256);
        }
        File oldFile = createTestFile("callback_old.bin", largeContent);
        
        byte[] modifiedContent = largeContent.clone();
        for (int i = 0; i < 1000; i++) {
            modifiedContent[i * 100] = (byte) ((modifiedContent[i * 100] + 1) % 256);
        }
        File newFile = createTestFile("callback_new.bin", modifiedContent);
        File patchFile = new File(testDir, "callback_test.patch");
        
        final AtomicInteger callbackCount = new AtomicInteger(0);
        final AtomicBoolean progressIncreasing = new AtomicBoolean(true);
        final long[] lastProgress = {-1};
        
        NativeProgressCallback callback = new NativeProgressCallback() {
            @Override
            public void onProgress(long current, long total) {
                callbackCount.incrementAndGet();
                if (current < lastProgress[0]) {
                    progressIncreasing.set(false);
                }
                lastProgress[0] = current;
                assertTrue("Current should be <= total", current <= total);
            }
        };
        
        int result = engine.generateDiff(
                oldFile.getAbsolutePath(),
                newFile.getAbsolutePath(),
                patchFile.getAbsolutePath(),
                callback
        );
        
        assertEquals("generateDiff should succeed", NativePatchEngine.SUCCESS, result);
        // 注意：回调次数取决于实现，可能为 0 或更多
        // 主要验证回调不会导致崩溃
    }
    
    @Test
    public void testGlobalProgressCallback() throws IOException {
        // 测试全局进度回调
        // Requirements: 11.6
        assumeNativeAvailable();
        
        final AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        
        NativeProgressCallback globalCallback = new NativeProgressCallback() {
            @Override
            public void onProgress(long current, long total) {
                callbackInvoked.set(true);
            }
        };
        
        engine.setProgressCallback(globalCallback);
        assertEquals("Global callback should be set", globalCallback, engine.getProgressCallback());
        
        // 清除回调
        engine.setProgressCallback(null);
        assertNull("Global callback should be cleared", engine.getProgressCallback());
    }
    
    // ========================================================================
    // 取消机制测试 (Requirements: 11.7)
    // ========================================================================
    
    @Test
    public void testCancelOperation() {
        // 测试取消操作
        // Requirements: 11.7
        assumeNativeAvailable();
        
        assertFalse("Should not be cancelled initially", engine.isCancelled());
        
        engine.cancel();
        
        assertTrue("Should be cancelled after cancel()", engine.isCancelled());
    }
    
    // ========================================================================
    // 错误处理测试 (Requirements: 11.9)
    // ========================================================================
    
    @Test
    public void testErrorToString() {
        // 测试错误码转字符串
        // Requirements: 11.9
        assumeNativeAvailable();
        
        String successMsg = engine.errorToString(NativePatchEngine.SUCCESS);
        assertNotNull("Error message should not be null", successMsg);
        
        String fileNotFoundMsg = engine.errorToString(NativePatchEngine.ERROR_FILE_NOT_FOUND);
        assertNotNull("Error message should not be null", fileNotFoundMsg);
        assertFalse("Error message should not be empty", fileNotFoundMsg.isEmpty());
        
        String corruptPatchMsg = engine.errorToString(NativePatchEngine.ERROR_CORRUPT_PATCH);
        assertNotNull("Error message should not be null", corruptPatchMsg);
    }
    
    @Test
    public void testGetLastError() {
        // 测试获取最后错误信息
        // Requirements: 11.9
        assumeNativeAvailable();
        
        // 触发一个错误
        File nonExistentFile = new File(testDir, "trigger_error.bin");
        engine.generateDiff(
                nonExistentFile.getAbsolutePath(),
                nonExistentFile.getAbsolutePath(),
                new File(testDir, "error.patch").getAbsolutePath(),
                null
        );
        
        String lastError = engine.getLastError();
        assertNotNull("Last error should not be null", lastError);
    }
    
    // ========================================================================
    // 异步操作测试 (Requirements: 11.10)
    // ========================================================================
    
    @Test
    public void testAsyncGenerateDiff() throws IOException, InterruptedException {
        // 测试异步生成差异
        // Requirements: 11.10
        assumeNativeAvailable();
        
        File oldFile = createTestFile("async_old.bin", "Async test original content.");
        File newFile = createTestFile("async_new.bin", "Async test modified content with changes.");
        File patchFile = new File(testDir, "async_test.patch");
        
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);
        final AtomicReference<String> errorMsg = new AtomicReference<>();
        
        Thread thread = engine.generateDiffAsync(
                oldFile.getAbsolutePath(),
                newFile.getAbsolutePath(),
                patchFile.getAbsolutePath(),
                null,
                new NativePatchEngine.AsyncResultCallback() {
                    @Override
                    public void onSuccess() {
                        success.set(true);
                        latch.countDown();
                    }
                    
                    @Override
                    public void onError(int errorCode, String message) {
                        errorMsg.set(message);
                        latch.countDown();
                    }
                }
        );
        
        assertNotNull("Thread should be returned", thread);
        assertTrue("Should complete within timeout", latch.await(30, TimeUnit.SECONDS));
        assertTrue("Async operation should succeed: " + errorMsg.get(), success.get());
        assertTrue("Patch file should be created", patchFile.exists());
    }
    
    @Test
    public void testAsyncCalculateMd5() throws IOException, InterruptedException {
        // 测试异步计算 MD5
        // Requirements: 11.10
        assumeNativeAvailable();
        
        File testFile = createTestFile("async_md5.txt", "Content for async MD5 test.");
        
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> hashResult = new AtomicReference<>();
        
        Thread thread = engine.calculateMd5Async(
                testFile.getAbsolutePath(),
                new NativePatchEngine.HashResultCallback() {
                    @Override
                    public void onSuccess(String hash) {
                        hashResult.set(hash);
                        latch.countDown();
                    }
                    
                    @Override
                    public void onError(int errorCode, String message) {
                        latch.countDown();
                    }
                }
        );
        
        assertNotNull("Thread should be returned", thread);
        assertTrue("Should complete within timeout", latch.await(10, TimeUnit.SECONDS));
        assertNotNull("Hash should be calculated", hashResult.get());
        assertEquals("MD5 should be 32 characters", 32, hashResult.get().length());
    }
    
    // ========================================================================
    // NativePatchException 测试
    // ========================================================================
    
    @Test
    public void testNativePatchException() {
        // 测试 NativePatchException 类
        NativePatchException exception = new NativePatchException(
                NativePatchEngine.ERROR_FILE_NOT_FOUND,
                "Test error message"
        );
        
        assertEquals("Error code should match",
                NativePatchEngine.ERROR_FILE_NOT_FOUND, exception.getErrorCode());
        assertEquals("Message should match", "Test error message", exception.getMessage());
        assertTrue("isFileNotFound should return true", exception.isFileNotFound());
        assertFalse("isCancelled should return false", exception.isCancelled());
        assertFalse("isOutOfMemory should return false", exception.isOutOfMemory());
        assertFalse("isCorruptPatch should return false", exception.isCorruptPatch());
    }
    
    @Test
    public void testNativePatchException_Cancelled() {
        NativePatchException exception = new NativePatchException(
                NativePatchEngine.ERROR_CANCELLED,
                "Operation cancelled"
        );
        
        assertTrue("isCancelled should return true", exception.isCancelled());
        assertFalse("isFileNotFound should return false", exception.isFileNotFound());
    }
    
    @Test
    public void testNativePatchException_ToString() {
        NativePatchException exception = new NativePatchException(
                NativePatchEngine.ERROR_OUT_OF_MEMORY,
                "Out of memory"
        );
        
        String str = exception.toString();
        assertNotNull("toString should not be null", str);
        assertTrue("toString should contain error code", str.contains(String.valueOf(NativePatchEngine.ERROR_OUT_OF_MEMORY)));
        assertTrue("toString should contain message", str.contains("Out of memory"));
    }
    
    // ========================================================================
    // 辅助方法
    // ========================================================================
    
    private void assumeNativeAvailable() {
        if (!NativePatchEngine.isAvailable()) {
            // 跳过测试如果 Native 库不可用
            org.junit.Assume.assumeTrue("Native library not available, skipping test", false);
        }
    }
    
    private File createTestFile(String name, String content) throws IOException {
        return createTestFile(name, content.getBytes(StandardCharsets.UTF_8));
    }
    
    private File createTestFile(String name, byte[] content) throws IOException {
        File file = new File(testDir, name);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content);
        }
        return file;
    }
}
