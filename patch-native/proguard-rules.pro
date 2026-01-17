# Add project specific ProGuard rules here.
# Native library rules

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep NativePatchEngine class
-keep class com.orange.patchnative.NativePatchEngine { *; }
-keep class com.orange.patchnative.NativeProgressCallback { *; }
