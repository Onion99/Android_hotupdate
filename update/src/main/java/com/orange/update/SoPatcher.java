package com.orange.update;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * SO 库补丁器，负责将补丁中的 SO 库加载到应用中。
 * 
 * 技术原理：
 * 通过反射修改 ClassLoader 的 nativeLibraryDirectories 和 nativeLibraryPathElements，
 * 使应用能够加载补丁中的 SO 库。
 * 
 * 补丁包结构：
 * patch.zip
 * ├── lib/
 * │   ├── armeabi-v7a/
 * │   │   └── libxxx.so
 * │   ├── arm64-v8a/
 * │   │   └── libxxx.so
 * │   ├── x86/
 * │   │   └── libxxx.so
 * │   └── x86_64/
 * │       └── libxxx.so
 * └── assets/
 *     └── so_meta.txt  # SO 库元数据
 * 
 * 兼容性：
 * - API 21-22: 修改 DexPathList.nativeLibraryDirectories
 * - API 23+: 修改 DexPathList.nativeLibraryPathElements
 */
public class SoPatcher {
    
    private static final String TAG = "SoPatcher";
    
    // SO 库目录前缀
    private static final String LIB_DIR_PREFIX = "lib/";
    
    // SO 元数据文件
    private static final String SO_META_FILE = "assets/so_meta.txt";
    
    // 支持的 ABI
    private static final String[] SUPPORTED_ABIS = Build.SUPPORTED_ABIS;
    
    /**
     * 从补丁包加载 SO 库
     * 
     * @param context 应用上下文
     * @param patchFile 补丁文件
     * @throws PatchSoException 如果加载失败
     */
    public static void loadPatchLibraries(Context context, File patchFile) 
            throws PatchSoException {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        if (patchFile == null || !patchFile.exists()) {
            throw new PatchSoException(UpdateErrorCode.ERROR_FILE_NOT_FOUND,
                    "Patch file not found: " + patchFile);
        }
        
        try {
            Log.d(TAG, "Loading SO libraries from patch: " + patchFile.getAbsolutePath());
            
            // 1. 检查补丁包是否包含 SO 库
            if (!hasSoLibraries(patchFile)) {
                Log.d(TAG, "No SO libraries found in patch, skipping");
                return;
            }
            
            // 2. 提取 SO 库到临时目录
            File soDir = extractSoLibraries(context, patchFile);
            if (soDir == null || !soDir.exists()) {
                Log.w(TAG, "Failed to extract SO libraries");
                return;
            }
            
            // 3. 注入 SO 库路径到 ClassLoader
            injectSoPath(context, soDir);
            
            Log.i(TAG, "SO libraries loaded successfully from: " + soDir.getAbsolutePath());
            
        } catch (PatchSoException e) {
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load SO libraries", e);
            throw new PatchSoException(UpdateErrorCode.ERROR_APPLY_FAILED,
                    "Failed to load SO libraries: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查补丁包是否包含 SO 库
     */
    private static boolean hasSoLibraries(File patchFile) {
        try (ZipFile zipFile = new ZipFile(patchFile)) {
            java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                // 检查是否有 lib/ 目录下的 .so 文件
                if (name.startsWith(LIB_DIR_PREFIX) && name.endsWith(".so")) {
                    Log.d(TAG, "Found SO library: " + name);
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to check SO libraries", e);
        }
        return false;
    }
    
    /**
     * 提取 SO 库到临时目录
     * 
     * @return SO 库根目录（包含各个 ABI 子目录）
     */
    private static File extractSoLibraries(Context context, File patchFile) throws Exception {
        // 创建 SO 库目录
        File soRootDir = new File(context.getFilesDir(), "hotupdate/lib");
        if (!soRootDir.exists()) {
            soRootDir.mkdirs();
        }
        
        // 清空旧的 SO 库
        deleteDirectory(soRootDir);
        soRootDir.mkdirs();
        
        // 获取当前设备的主 ABI
        String primaryAbi = Build.SUPPORTED_ABIS[0];
        Log.d(TAG, "Device primary ABI: " + primaryAbi);
        
        // 提取 SO 库
        try (ZipFile zipFile = new ZipFile(patchFile)) {
            java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                
                // 只提取 lib/ 目录下的 .so 文件
                if (name.startsWith(LIB_DIR_PREFIX) && name.endsWith(".so") && !entry.isDirectory()) {
                    // 提取 ABI 和文件名
                    // 例如: lib/arm64-v8a/libtest.so -> arm64-v8a/libtest.so
                    String relativePath = name.substring(LIB_DIR_PREFIX.length());
                    
                    // 检查是否是支持的 ABI
                    boolean isSupported = false;
                    for (String abi : SUPPORTED_ABIS) {
                        if (relativePath.startsWith(abi + "/")) {
                            isSupported = true;
                            break;
                        }
                    }
                    
                    if (!isSupported) {
                        Log.d(TAG, "Skipping unsupported ABI: " + relativePath);
                        continue;
                    }
                    
                    // 创建目标文件
                    File targetFile = new File(soRootDir, relativePath);
                    File parentDir = targetFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }
                    
                    // 提取文件
                    try (InputStream is = zipFile.getInputStream(entry);
                         FileOutputStream fos = new FileOutputStream(targetFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    
                    // 设置可执行权限
                    targetFile.setExecutable(true, false);
                    
                    Log.d(TAG, "Extracted SO library: " + relativePath + " -> " + targetFile.getAbsolutePath());
                }
            }
        }
        
        return soRootDir;
    }
    
    /**
     * 注入 SO 库路径到 ClassLoader
     * 
     * 参考 Tinker 的实现：
     * 1. 获取 PathClassLoader
     * 2. 获取 DexPathList
     * 3. 根据 Android 版本修改不同的字段
     */
    private static void injectSoPath(Context context, File soRootDir) throws Exception {
        // 获取 PathClassLoader
        ClassLoader classLoader = context.getClassLoader();
        
        // 获取 DexPathList
        Field pathListField = findField(classLoader.getClass(), "pathList");
        pathListField.setAccessible(true);
        Object dexPathList = pathListField.get(classLoader);
        
        if (dexPathList == null) {
            throw new PatchSoException(UpdateErrorCode.ERROR_APPLY_FAILED,
                    "DexPathList is null");
        }
        
        int sdkVersion = Build.VERSION.SDK_INT;
        Log.d(TAG, "Injecting SO path, SDK version: " + sdkVersion);
        
        if (sdkVersion >= Build.VERSION_CODES.M) {
            // Android 6.0+ (API 23+): 修改 nativeLibraryPathElements
            injectSoPathForM(dexPathList, soRootDir);
        } else {
            // Android 5.0-5.1 (API 21-22): 修改 nativeLibraryDirectories
            injectSoPathForLollipop(dexPathList, soRootDir);
        }
        
        Log.d(TAG, "SO path injected successfully");
    }
    
    /**
     * Android 5.0-5.1 (API 21-22) 的 SO 注入方案
     * 修改 DexPathList.nativeLibraryDirectories
     */
    private static void injectSoPathForLollipop(Object dexPathList, File soRootDir) throws Exception {
        // 获取 nativeLibraryDirectories 字段
        Field nativeLibraryDirectoriesField = findField(dexPathList.getClass(), "nativeLibraryDirectories");
        nativeLibraryDirectoriesField.setAccessible(true);
        
        File[] oldDirs = (File[]) nativeLibraryDirectoriesField.get(dexPathList);
        
        // 获取所有 ABI 目录
        List<File> newDirs = new ArrayList<>();
        for (String abi : SUPPORTED_ABIS) {
            File abiDir = new File(soRootDir, abi);
            if (abiDir.exists() && abiDir.isDirectory()) {
                newDirs.add(abiDir);
                Log.d(TAG, "Added SO directory: " + abiDir.getAbsolutePath());
            }
        }
        
        // 合并新旧目录（补丁目录在前，优先加载）
        if (oldDirs != null) {
            for (File dir : oldDirs) {
                if (dir != null && !newDirs.contains(dir)) {
                    newDirs.add(dir);
                }
            }
        }
        
        // 设置新的目录数组
        File[] newDirsArray = newDirs.toArray(new File[0]);
        nativeLibraryDirectoriesField.set(dexPathList, newDirsArray);
        
        Log.d(TAG, "Updated nativeLibraryDirectories, count: " + newDirsArray.length);
    }
    
    /**
     * Android 6.0+ (API 23+) 的 SO 注入方案
     * 修改 DexPathList.nativeLibraryPathElements
     */
    private static void injectSoPathForM(Object dexPathList, File soRootDir) throws Exception {
        // 获取 nativeLibraryPathElements 字段
        Field nativeLibraryPathElementsField = findField(dexPathList.getClass(), "nativeLibraryPathElements");
        nativeLibraryPathElementsField.setAccessible(true);
        
        Object[] oldElements = (Object[]) nativeLibraryPathElementsField.get(dexPathList);
        
        // 创建新的 Element 对象
        List<Object> newElements = new ArrayList<>();
        
        // 获取 Element 类
        Class<?> elementClass = null;
        if (oldElements != null && oldElements.length > 0) {
            elementClass = oldElements[0].getClass();
        } else {
            // 尝试查找 Element 类
            Class<?>[] innerClasses = dexPathList.getClass().getDeclaredClasses();
            for (Class<?> clazz : innerClasses) {
                if (clazz.getSimpleName().equals("Element")) {
                    elementClass = clazz;
                    break;
                }
            }
        }
        
        if (elementClass == null) {
            throw new PatchSoException(UpdateErrorCode.ERROR_APPLY_FAILED,
                    "Cannot find Element class");
        }
        
        // 为每个 ABI 创建 Element
        for (String abi : SUPPORTED_ABIS) {
            File abiDir = new File(soRootDir, abi);
            if (abiDir.exists() && abiDir.isDirectory()) {
                Object element = createNativeLibraryElement(elementClass, abiDir);
                if (element != null) {
                    newElements.add(element);
                    Log.d(TAG, "Created Element for: " + abiDir.getAbsolutePath());
                }
            }
        }
        
        // 合并新旧 Element（补丁 Element 在前，优先加载）
        if (oldElements != null) {
            for (Object element : oldElements) {
                if (element != null) {
                    newElements.add(element);
                }
            }
        }
        
        // 设置新的 Element 数组
        Object[] newElementsArray = newElements.toArray((Object[]) Array.newInstance(elementClass, 0));
        nativeLibraryPathElementsField.set(dexPathList, newElementsArray);
        
        Log.d(TAG, "Updated nativeLibraryPathElements, count: " + newElementsArray.length);
    }
    
    /**
     * 创建 NativeLibraryElement 对象
     * 
     * Element 构造函数签名（Android 6.0+）：
     * Element(File dir, boolean isDirectory, File zip, DexFile dexFile)
     * 或
     * Element(File path)
     */
    private static Object createNativeLibraryElement(Class<?> elementClass, File dir) {
        try {
            // 尝试使用 File 参数的构造函数（Android 7.0+）
            try {
                java.lang.reflect.Constructor<?> constructor = elementClass.getDeclaredConstructor(File.class);
                constructor.setAccessible(true);
                return constructor.newInstance(dir);
            } catch (NoSuchMethodException e) {
                // 继续尝试其他构造函数
            }
            
            // 尝试使用多参数构造函数（Android 6.0-6.1）
            try {
                java.lang.reflect.Constructor<?> constructor = elementClass.getDeclaredConstructor(
                        File.class, boolean.class, File.class, Class.forName("dalvik.system.DexFile"));
                constructor.setAccessible(true);
                return constructor.newInstance(dir, true, null, null);
            } catch (NoSuchMethodException e) {
                // 继续尝试其他构造函数
            }
            
            Log.w(TAG, "Cannot find suitable Element constructor");
            return null;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to create Element", e);
            return null;
        }
    }
    
    /**
     * 查找字段（包括父类）
     */
    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field " + fieldName + " not found in " + clazz.getName());
    }
    
    /**
     * 递归删除目录
     */
    private static void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        
        dir.delete();
    }
    
    /**
     * 检查当前 Android 版本是否支持 SO 热更新
     * 
     * @return 是否支持
     */
    public static boolean isSupported() {
        int sdkVersion = Build.VERSION.SDK_INT;
        // 支持 Android 5.0 (API 21) 到 Android 14 (API 34)
        return sdkVersion >= Build.VERSION_CODES.LOLLIPOP && sdkVersion <= 34;
    }
    
    /**
     * 获取当前 Android 版本的兼容性级别
     * 
     * @return 兼容性级别描述
     */
    public static String getCompatibilityLevel() {
        int sdkVersion = Build.VERSION.SDK_INT;
        
        if (sdkVersion < Build.VERSION_CODES.LOLLIPOP) {
            return "UNSUPPORTED: Android version too low (< 5.0)";
        } else if (sdkVersion < Build.VERSION_CODES.M) {
            return "FULL: nativeLibraryDirectories modification (Android 5.0-5.1)";
        } else if (sdkVersion <= 34) {
            return "FULL: nativeLibraryPathElements modification (Android 6.0+)";
        } else {
            return "UNKNOWN: Untested Android version (> 14)";
        }
    }
    
    /**
     * SO 补丁异常类
     */
    public static class PatchSoException extends Exception {
        private final int errorCode;
        
        public PatchSoException(int errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }
        
        public PatchSoException(int errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }
        
        public int getErrorCode() {
            return errorCode;
        }
    }
}
