# Consumer ProGuard rules for patch-generator-android

# Keep the AndroidPatchGenerator public API
-keep public class com.orange.patchgen.android.AndroidPatchGenerator { *; }
-keep public class com.orange.patchgen.android.AndroidPatchGenerator$Builder { *; }
-keep public class com.orange.patchgen.android.StorageChecker { *; }
-keep public class com.orange.patchgen.android.AndroidGeneratorCallback { *; }

# Keep callback interfaces
-keep public interface com.orange.patchgen.android.AndroidGeneratorCallback { *; }
