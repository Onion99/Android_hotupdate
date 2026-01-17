package com.orange.patchgen.android;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * StorageChecker 单元测试
 */
public class StorageCheckerTest {
    
    @Test
    public void testFormatSize_bytes() {
        assertEquals("100 B", StorageChecker.formatSize(100));
    }
    
    @Test
    public void testFormatSize_kilobytes() {
        assertEquals("1.0 KB", StorageChecker.formatSize(1024));
        assertEquals("10.0 KB", StorageChecker.formatSize(10 * 1024));
    }
    
    @Test
    public void testFormatSize_megabytes() {
        assertEquals("1.0 MB", StorageChecker.formatSize(1024 * 1024));
        assertEquals("100.0 MB", StorageChecker.formatSize(100 * 1024 * 1024));
    }
    
    @Test
    public void testFormatSize_gigabytes() {
        assertEquals("1.00 GB", StorageChecker.formatSize(1024L * 1024 * 1024));
        assertEquals("2.50 GB", StorageChecker.formatSize((long) (2.5 * 1024 * 1024 * 1024)));
    }
    
    @Test
    public void testStorageCheckResult() {
        StorageChecker.StorageCheckResult result = new StorageChecker.StorageCheckResult(
                true, 
                100 * 1024 * 1024L, 
                50 * 1024 * 1024L, 
                "Test message"
        );
        
        assertTrue(result.isSufficient());
        assertEquals(100 * 1024 * 1024L, result.getAvailableBytes());
        assertEquals(50 * 1024 * 1024L, result.getRequiredBytes());
        assertEquals(100, result.getAvailableMB());
        assertEquals(50, result.getRequiredMB());
        assertEquals("Test message", result.getMessage());
    }
    
    @Test
    public void testStorageCheckResult_insufficient() {
        StorageChecker.StorageCheckResult result = new StorageChecker.StorageCheckResult(
                false, 
                30 * 1024 * 1024L, 
                50 * 1024 * 1024L, 
                "Insufficient storage"
        );
        
        assertFalse(result.isSufficient());
        assertEquals(30, result.getAvailableMB());
        assertEquals(50, result.getRequiredMB());
    }
}
