package com.orange.patchgen.android;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    
    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("com.orange.patchgen.android.test", appContext.getPackageName());
    }
    
    @Test
    public void testStorageChecker_creation() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        StorageChecker checker = new StorageChecker(appContext);
        assertNotNull(checker);
        assertNotNull(checker.getDefaultOutputDir());
        assertNotNull(checker.getTempDir());
    }
    
    @Test
    public void testStorageChecker_availableSpace() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        StorageChecker checker = new StorageChecker(appContext);
        
        long internalSpace = checker.getInternalStorageAvailable();
        assertTrue("Internal storage should be positive", internalSpace > 0);
    }
}
