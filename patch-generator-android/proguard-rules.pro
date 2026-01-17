# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep the AndroidPatchGenerator public API
-keep public class com.orange.patchgen.android.AndroidPatchGenerator { *; }
-keep public class com.orange.patchgen.android.AndroidPatchGenerator$Builder { *; }
-keep public class com.orange.patchgen.android.StorageChecker { *; }
-keep public class com.orange.patchgen.android.AndroidGeneratorCallback { *; }

# Keep callback interfaces
-keep public interface com.orange.patchgen.android.AndroidGeneratorCallback { *; }
