package com.orange.update;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import android.util.ArrayMap;
import android.util.Log;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 资源补丁器，负责将补丁资源加载到应用中。
 * 
 * 技术原理：
 * 通过反射替换 AssetManager 和 Resources 对象，使应用能够加载补丁中的资源。
 * 补丁资源路径添加到 AssetManager 后，会优先于原始资源被加载。
 * 
 * 兼容性：
 * - API 21-23: 标准 AssetManager 替换
 * - API 24-27: 需要处理 ResourcesImpl
 * - API 28+: 需要处理 ResourcesManager 缓存
 */
public class ResourcePatcher {
    
    private static final String TAG = "ResourcePatcher";
    
    // 反射字段名
    private static final String FIELD_M_ASSETS = "mAssets";
    private static final String FIELD_M_RESOURCES = "mResources";
    private static final String FIELD_M_RESOURCES_IMPL = "mResourcesImpl";
    private static final String FIELD_M_ACTIVE_RESOURCES = "mActiveResources";
    private static final String FIELD_M_RESOURCE_REFERENCES = "mResourceReferences";
    private static final String FIELD_M_RESOURCE_IMPLS = "mResourceImpls";
    
    // 方法名
    private static final String METHOD_ADD_ASSET_PATH = "addAssetPath";
    private static final String METHOD_ENSURE_STRING_BLOCKS = "ensureStringBlocks";
    
    /**
     * 加载补丁资源
     * 
     * @param context 应用上下文
     * @param patchResourcePath 补丁资源路径（通常是包含 resources.arsc 的 apk 或 zip 文件）
     * @throws PatchResourceException 如果加载失败
     */
    public static void loadPatchResources(Context context, String patchResourcePath) 
            throws PatchResourceException {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        if (patchResourcePath == null || patchResourcePath.isEmpty()) {
            throw new IllegalArgumentException("Patch resource path cannot be null or empty");
        }
        
        File patchFile = new File(patchResourcePath);
        if (!patchFile.exists()) {
            throw new PatchResourceException(UpdateErrorCode.ERROR_FILE_NOT_FOUND,
                    "Patch resource file not found: " + patchResourcePath);
        }
        
        try {
            int sdkVersion = Build.VERSION.SDK_INT;
            Log.d(TAG, "Loading patch resources, SDK version: " + sdkVersion);
            
            if (sdkVersion >= Build.VERSION_CODES.P) {
                // Android 9.0+ (API 28+)
                loadPatchResourcesForP(context, patchResourcePath);
            } else if (sdkVersion >= Build.VERSION_CODES.N) {
                // Android 7.0+ (API 24+)
                loadPatchResourcesForN(context, patchResourcePath);
            } else {
                // Android 5.0-6.0 (API 21-23)
                loadPatchResourcesStandard(context, patchResourcePath);
            }
            
            Log.i(TAG, "Patch resources loaded successfully: " + patchResourcePath);
            
        } catch (PatchResourceException e) {
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load patch resources", e);
            throw new PatchResourceException(UpdateErrorCode.ERROR_APPLY_FAILED,
                    "Failed to load patch resources: " + e.getMessage(), e);
        }
    }

    
    /**
     * 标准资源加载方案 (Android 5.0-6.0, API 21-23)
     */
    private static void loadPatchResourcesStandard(Context context, String patchResourcePath) 
            throws Exception {
        // 1. 创建新的 AssetManager
        AssetManager newAssetManager = AssetManager.class.newInstance();
        
        // 2. 通过反射调用 addAssetPath 方法
        Method addAssetPathMethod = AssetManager.class.getDeclaredMethod(
                METHOD_ADD_ASSET_PATH, String.class);
        addAssetPathMethod.setAccessible(true);
        
        // 先添加原始 APK 路径
        int result1 = (int) addAssetPathMethod.invoke(newAssetManager, 
                context.getPackageResourcePath());
        if (result1 == 0) {
            throw new PatchResourceException(UpdateErrorCode.ERROR_APPLY_FAILED,
                    "Failed to add original APK path to AssetManager");
        }
        
        // 再添加补丁资源路径
        int result2 = (int) addAssetPathMethod.invoke(newAssetManager, patchResourcePath);
        if (result2 == 0) {
            throw new PatchResourceException(UpdateErrorCode.ERROR_APPLY_FAILED,
                    "Failed to add patch resource path to AssetManager");
        }
        
        // 3. 确保字符串块已加载
        ensureStringBlocks(newAssetManager);
        
        // 4. 创建新的 Resources 对象
        Resources originalResources = context.getResources();
        Resources newResources = new Resources(
                newAssetManager,
                originalResources.getDisplayMetrics(),
                originalResources.getConfiguration()
        );
        
        // 5. 替换 Application 和所有 Activity 的 Resources
        replaceApplicationResources(context, newAssetManager, newResources);
        
        Log.d(TAG, "Patch resources loaded (standard method)");
    }
    
    /**
     * Android 7.0+ 资源加载方案 (API 24-27)
     * 需要处理 ResourcesImpl
     */
    private static void loadPatchResourcesForN(Context context, String patchResourcePath) 
            throws Exception {
        // 1. 创建新的 AssetManager
        AssetManager newAssetManager = AssetManager.class.newInstance();
        
        // 2. 添加资源路径
        Method addAssetPathMethod = AssetManager.class.getDeclaredMethod(
                METHOD_ADD_ASSET_PATH, String.class);
        addAssetPathMethod.setAccessible(true);
        
        addAssetPathMethod.invoke(newAssetManager, context.getPackageResourcePath());
        addAssetPathMethod.invoke(newAssetManager, patchResourcePath);
        
        // 3. 确保字符串块已加载
        ensureStringBlocks(newAssetManager);
        
        // 4. 替换 ResourcesImpl 中的 AssetManager
        replaceResourcesImplAssets(context, newAssetManager);
        
        Log.d(TAG, "Patch resources loaded (Android N method)");
    }
    
    /**
     * Android 9.0+ 资源加载方案 (API 28+)
     * 需要处理 ResourcesManager 缓存
     */
    private static void loadPatchResourcesForP(Context context, String patchResourcePath) 
            throws Exception {
        // 1. 创建新的 AssetManager
        AssetManager newAssetManager = AssetManager.class.newInstance();
        
        // 2. 添加资源路径
        Method addAssetPathMethod = AssetManager.class.getDeclaredMethod(
                METHOD_ADD_ASSET_PATH, String.class);
        addAssetPathMethod.setAccessible(true);
        
        addAssetPathMethod.invoke(newAssetManager, context.getPackageResourcePath());
        addAssetPathMethod.invoke(newAssetManager, patchResourcePath);
        
        // 3. 确保字符串块已加载
        ensureStringBlocks(newAssetManager);
        
        // 4. 清理 ResourcesManager 缓存并替换 AssetManager
        clearResourcesManagerCache(context, newAssetManager);
        
        Log.d(TAG, "Patch resources loaded (Android P method)");
    }

    
    // ==================== 反射工具方法 ====================
    
    /**
     * 确保 AssetManager 的字符串块已加载
     */
    private static void ensureStringBlocks(AssetManager assetManager) {
        try {
            Method ensureStringBlocksMethod = AssetManager.class.getDeclaredMethod(
                    METHOD_ENSURE_STRING_BLOCKS);
            ensureStringBlocksMethod.setAccessible(true);
            ensureStringBlocksMethod.invoke(assetManager);
        } catch (Exception e) {
            // 某些版本可能没有这个方法，忽略错误
            Log.w(TAG, "ensureStringBlocks not available: " + e.getMessage());
        }
    }
    
    /**
     * 替换 Application 和所有 Activity 的 Resources
     */
    private static void replaceApplicationResources(Context context, AssetManager newAssetManager, 
            Resources newResources) throws Exception {
        
        // 获取 Application 上下文
        Context appContext = context.getApplicationContext();
        
        // 1. 替换 ContextImpl 中的 mResources
        replaceContextResources(appContext, newResources);
        
        // 2. 替换 LoadedApk 中的 mResources
        replaceLoadedApkResources(appContext, newResources);
        
        // 3. 替换 ResourcesManager 中缓存的 Resources
        replaceResourcesManagerResources(appContext, newAssetManager, newResources);
        
        // 4. 如果是 Activity，也替换 Activity 的 Resources
        if (context instanceof Activity) {
            replaceContextResources(context, newResources);
        }
    }
    
    /**
     * 替换 Context 中的 mResources 字段
     */
    private static void replaceContextResources(Context context, Resources newResources) 
            throws Exception {
        try {
            Field resourcesField = context.getClass().getDeclaredField(FIELD_M_RESOURCES);
            resourcesField.setAccessible(true);
            resourcesField.set(context, newResources);
        } catch (NoSuchFieldException e) {
            // 尝试从父类查找
            Class<?> clazz = context.getClass().getSuperclass();
            while (clazz != null) {
                try {
                    Field resourcesField = clazz.getDeclaredField(FIELD_M_RESOURCES);
                    resourcesField.setAccessible(true);
                    resourcesField.set(context, newResources);
                    return;
                } catch (NoSuchFieldException ex) {
                    clazz = clazz.getSuperclass();
                }
            }
            Log.w(TAG, "mResources field not found in context hierarchy");
        }
    }
    
    /**
     * 替换 LoadedApk 中的 mResources 字段
     */
    private static void replaceLoadedApkResources(Context context, Resources newResources) 
            throws Exception {
        try {
            // 获取 ContextImpl 的 mPackageInfo (LoadedApk)
            Field packageInfoField = context.getClass().getDeclaredField("mPackageInfo");
            packageInfoField.setAccessible(true);
            Object loadedApk = packageInfoField.get(context);
            
            if (loadedApk != null) {
                Field resourcesField = loadedApk.getClass().getDeclaredField(FIELD_M_RESOURCES);
                resourcesField.setAccessible(true);
                resourcesField.set(loadedApk, newResources);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to replace LoadedApk resources: " + e.getMessage());
        }
    }
    
    /**
     * 替换 ResourcesManager 中缓存的 Resources
     */
    @SuppressWarnings("unchecked")
    private static void replaceResourcesManagerResources(Context context, 
            AssetManager newAssetManager, Resources newResources) throws Exception {
        try {
            // 获取 ResourcesManager 单例
            Class<?> resourcesManagerClass = Class.forName("android.app.ResourcesManager");
            Method getInstanceMethod = resourcesManagerClass.getDeclaredMethod("getInstance");
            getInstanceMethod.setAccessible(true);
            Object resourcesManager = getInstanceMethod.invoke(null);
            
            if (resourcesManager == null) {
                return;
            }
            
            // 根据 Android 版本处理不同的缓存字段
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+ 使用 mResourceImpls
                replaceResourceImpls(resourcesManager, newAssetManager);
            } else {
                // Android 5.0-6.0 使用 mActiveResources
                replaceActiveResources(resourcesManager, newAssetManager, newResources);
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Failed to replace ResourcesManager resources: " + e.getMessage());
        }
    }

    
    /**
     * 替换 ResourcesManager 中的 mActiveResources (Android 5.0-6.0)
     */
    @SuppressWarnings("unchecked")
    private static void replaceActiveResources(Object resourcesManager, 
            AssetManager newAssetManager, Resources newResources) throws Exception {
        Field activeResourcesField = resourcesManager.getClass()
                .getDeclaredField(FIELD_M_ACTIVE_RESOURCES);
        activeResourcesField.setAccessible(true);
        
        Object activeResources = activeResourcesField.get(resourcesManager);
        
        if (activeResources instanceof ArrayMap) {
            ArrayMap<Object, WeakReference<Resources>> map = 
                    (ArrayMap<Object, WeakReference<Resources>>) activeResources;
            
            for (int i = 0; i < map.size(); i++) {
                WeakReference<Resources> ref = map.valueAt(i);
                if (ref != null) {
                    Resources resources = ref.get();
                    if (resources != null) {
                        replaceResourcesAssets(resources, newAssetManager);
                    }
                }
            }
        } else if (activeResources instanceof HashMap) {
            HashMap<Object, WeakReference<Resources>> map = 
                    (HashMap<Object, WeakReference<Resources>>) activeResources;
            
            for (WeakReference<Resources> ref : map.values()) {
                if (ref != null) {
                    Resources resources = ref.get();
                    if (resources != null) {
                        replaceResourcesAssets(resources, newAssetManager);
                    }
                }
            }
        }
    }
    
    /**
     * 替换 ResourcesManager 中的 mResourceImpls (Android 7.0+)
     */
    @SuppressWarnings("unchecked")
    private static void replaceResourceImpls(Object resourcesManager, 
            AssetManager newAssetManager) throws Exception {
        try {
            Field resourceImplsField = resourcesManager.getClass()
                    .getDeclaredField(FIELD_M_RESOURCE_IMPLS);
            resourceImplsField.setAccessible(true);
            
            Object resourceImpls = resourceImplsField.get(resourcesManager);
            
            if (resourceImpls instanceof ArrayMap) {
                ArrayMap<Object, WeakReference<Object>> map = 
                        (ArrayMap<Object, WeakReference<Object>>) resourceImpls;
                
                for (int i = 0; i < map.size(); i++) {
                    WeakReference<Object> ref = map.valueAt(i);
                    if (ref != null) {
                        Object resourcesImpl = ref.get();
                        if (resourcesImpl != null) {
                            replaceResourcesImplAssetsDirect(resourcesImpl, newAssetManager);
                        }
                    }
                }
            }
        } catch (NoSuchFieldException e) {
            // 尝试使用 mResourceReferences
            replaceResourceReferences(resourcesManager, newAssetManager);
        }
    }
    
    /**
     * 替换 mResourceReferences 中的 AssetManager (Android 7.0+)
     */
    @SuppressWarnings("unchecked")
    private static void replaceResourceReferences(Object resourcesManager, 
            AssetManager newAssetManager) throws Exception {
        try {
            Field resourceRefsField = resourcesManager.getClass()
                    .getDeclaredField(FIELD_M_RESOURCE_REFERENCES);
            resourceRefsField.setAccessible(true);
            
            Object resourceRefs = resourceRefsField.get(resourcesManager);
            
            if (resourceRefs instanceof Collection) {
                Collection<WeakReference<Resources>> refs = 
                        (Collection<WeakReference<Resources>>) resourceRefs;
                
                for (WeakReference<Resources> ref : refs) {
                    if (ref != null) {
                        Resources resources = ref.get();
                        if (resources != null) {
                            replaceResourcesAssets(resources, newAssetManager);
                        }
                    }
                }
            }
        } catch (NoSuchFieldException e) {
            Log.w(TAG, "mResourceReferences field not found");
        }
    }
    
    /**
     * 替换 Resources 中的 mAssets 字段
     */
    private static void replaceResourcesAssets(Resources resources, AssetManager newAssetManager) 
            throws Exception {
        Field assetsField = Resources.class.getDeclaredField(FIELD_M_ASSETS);
        assetsField.setAccessible(true);
        assetsField.set(resources, newAssetManager);
    }
    
    /**
     * 替换 ResourcesImpl 中的 mAssets 字段 (Android 7.0+)
     */
    private static void replaceResourcesImplAssets(Context context, AssetManager newAssetManager) 
            throws Exception {
        Resources resources = context.getResources();
        
        try {
            // 获取 Resources 中的 mResourcesImpl
            Field resourcesImplField = Resources.class.getDeclaredField(FIELD_M_RESOURCES_IMPL);
            resourcesImplField.setAccessible(true);
            Object resourcesImpl = resourcesImplField.get(resources);
            
            if (resourcesImpl != null) {
                replaceResourcesImplAssetsDirect(resourcesImpl, newAssetManager);
            }
        } catch (NoSuchFieldException e) {
            // 如果没有 mResourcesImpl，直接替换 mAssets
            replaceResourcesAssets(resources, newAssetManager);
        }
    }
    
    /**
     * 直接替换 ResourcesImpl 中的 mAssets 字段
     */
    private static void replaceResourcesImplAssetsDirect(Object resourcesImpl, 
            AssetManager newAssetManager) throws Exception {
        Field assetsField = resourcesImpl.getClass().getDeclaredField(FIELD_M_ASSETS);
        assetsField.setAccessible(true);
        assetsField.set(resourcesImpl, newAssetManager);
    }

    
    /**
     * 清理 ResourcesManager 缓存并替换 AssetManager (Android 9.0+)
     */
    @SuppressWarnings("unchecked")
    private static void clearResourcesManagerCache(Context context, AssetManager newAssetManager) 
            throws Exception {
        try {
            // 获取 ResourcesManager 单例
            Class<?> resourcesManagerClass = Class.forName("android.app.ResourcesManager");
            Method getInstanceMethod = resourcesManagerClass.getDeclaredMethod("getInstance");
            getInstanceMethod.setAccessible(true);
            Object resourcesManager = getInstanceMethod.invoke(null);
            
            if (resourcesManager == null) {
                return;
            }
            
            // 替换所有 ResourcesImpl 中的 AssetManager
            replaceResourceImpls(resourcesManager, newAssetManager);
            
            // 替换 Application 的 Resources
            Context appContext = context.getApplicationContext();
            if (appContext instanceof Application) {
                replaceResourcesImplAssets(appContext, newAssetManager);
            }
            
            // 如果是 Activity，也替换
            if (context instanceof Activity) {
                replaceResourcesImplAssets(context, newAssetManager);
            }
            
        } catch (Exception e) {
            Log.w(TAG, "Failed to clear ResourcesManager cache: " + e.getMessage());
            // 回退到标准方法
            Resources originalResources = context.getResources();
            Resources newResources = new Resources(
                    newAssetManager,
                    originalResources.getDisplayMetrics(),
                    originalResources.getConfiguration()
            );
            replaceApplicationResources(context, newAssetManager, newResources);
        }
    }
    
    // ==================== 版本兼容性检查 ====================
    
    /**
     * 检查当前 Android 版本是否支持资源热更新
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
        } else if (sdkVersion <= Build.VERSION_CODES.M) {
            return "FULL: Standard AssetManager replacement (Android 5.0-6.0)";
        } else if (sdkVersion <= Build.VERSION_CODES.O_MR1) {
            return "FULL: ResourcesImpl support (Android 7.0-8.1)";
        } else if (sdkVersion <= 34) {
            return "LIMITED: ResourcesManager cache handling (Android 9.0+)";
        } else {
            return "UNKNOWN: Untested Android version (> 14)";
        }
    }
    
    // ==================== 调试工具方法 ====================
    
    /**
     * 打印当前 AssetManager 的资源路径信息（调试用）
     * 
     * @param context 应用上下文
     */
    public static void printAssetPaths(Context context) {
        try {
            AssetManager assetManager = context.getAssets();
            
            // 尝试获取 getApkAssets 方法 (Android 9.0+)
            try {
                Method getApkAssetsMethod = AssetManager.class.getDeclaredMethod("getApkAssets");
                getApkAssetsMethod.setAccessible(true);
                Object[] apkAssets = (Object[]) getApkAssetsMethod.invoke(assetManager);
                
                Log.d(TAG, "=== AssetManager Paths (API 28+) ===");
                if (apkAssets != null) {
                    for (int i = 0; i < apkAssets.length; i++) {
                        Log.d(TAG, "ApkAsset[" + i + "]: " + apkAssets[i]);
                    }
                }
                Log.d(TAG, "====================================");
                return;
            } catch (NoSuchMethodException e) {
                // 继续尝试其他方法
            }
            
            // 尝试获取 mStringBlocks 或其他字段来推断路径
            Log.d(TAG, "=== AssetManager Info ===");
            Log.d(TAG, "AssetManager: " + assetManager);
            Log.d(TAG, "=========================");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to print asset paths", e);
        }
    }
    
    /**
     * 检查补丁资源是否已加载
     * 
     * @param context 应用上下文
     * @param patchResourcePath 补丁资源路径
     * @return 是否已加载
     */
    public static boolean isPatchResourceLoaded(Context context, String patchResourcePath) {
        // 由于 AssetManager 没有直接的方法来检查已加载的路径，
        // 这里只能通过尝试加载补丁中的特定资源来判断
        // 实际使用时，可以在补丁中放置一个标记文件
        try {
            AssetManager assetManager = context.getAssets();
            // 尝试打开补丁中的标记文件
            assetManager.open("patch_marker.txt").close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 资源补丁异常类
     */
    public static class PatchResourceException extends Exception {
        private final int errorCode;
        
        public PatchResourceException(int errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }
        
        public PatchResourceException(int errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }
        
        public int getErrorCode() {
            return errorCode;
        }
    }
}
