package com.orange.patch.plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

/**
 * Gradle 插件入口类
 * 
 * 实现 Plugin<Project> 接口，注册 patchGenerator 扩展和 generatePatch 任务。
 * 
 * Requirements: 7.1, 7.3-7.5
 */
public class PatchPlugin implements Plugin<Project> {

    private static final String EXTENSION_NAME = "patchGenerator";
    private static final String TASK_GROUP = "patch";

    @Override
    public void apply(Project project) {
        Logger logger = project.getLogger();
        logger.info("Applying Patch Generator Plugin to project: {}", project.getName());

        // 创建扩展配置
        PatchExtension extension = project.getExtensions()
                .create(EXTENSION_NAME, PatchExtension.class, project);

        // 在项目评估完成后注册任务
        project.afterEvaluate(p -> {
            if (!extension.isEnabled()) {
                logger.info("Patch Generator Plugin is disabled");
                return;
            }

            // 验证配置
            try {
                extension.validate();
            } catch (IllegalStateException e) {
                logger.warn("Patch Generator Plugin configuration is incomplete: {}", e.getMessage());
                return;
            }

            // 注册生成补丁任务
            registerGeneratePatchTask(p, extension);
        });
    }

    /**
     * 注册生成补丁任务
     */
    private void registerGeneratePatchTask(Project project, PatchExtension extension) {
        Logger logger = project.getLogger();

        // 检查是否是 Android 项目
        boolean isAndroidProject = project.getPlugins().hasPlugin("com.android.application");
        
        if (isAndroidProject) {
            // Android 项目：为每个 variant 注册任务
            registerAndroidTasks(project, extension);
        } else {
            // 非 Android 项目：注册通用任务
            registerGenericTask(project, extension);
        }
    }

    /**
     * 为 Android 项目注册任务
     */
    private void registerAndroidTasks(Project project, PatchExtension extension) {
        Logger logger = project.getLogger();
        
        // 尝试获取 Android 扩展
        try {
            Object androidExtension = project.getExtensions().findByName("android");
            if (androidExtension == null) {
                logger.warn("Android extension not found, registering generic task");
                registerGenericTask(project, extension);
                return;
            }

            // 使用反射获取 applicationVariants
            java.lang.reflect.Method getVariantsMethod = androidExtension.getClass()
                    .getMethod("getApplicationVariants");
            Object variants = getVariantsMethod.invoke(androidExtension);
            
            if (variants instanceof Iterable) {
                for (Object variant : (Iterable<?>) variants) {
                    registerVariantTask(project, extension, variant);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to register Android variant tasks: {}", e.getMessage());
            registerGenericTask(project, extension);
        }
    }

    /**
     * 为单个 variant 注册任务
     */
    private void registerVariantTask(Project project, PatchExtension extension, Object variant) {
        Logger logger = project.getLogger();
        
        try {
            // 获取 variant 名称
            java.lang.reflect.Method getNameMethod = variant.getClass().getMethod("getName");
            String variantName = (String) getNameMethod.invoke(variant);
            
            String taskName = "generate" + capitalize(variantName) + "Patch";
            
            // 检查任务是否已存在
            if (project.getTasks().findByName(taskName) != null) {
                return;
            }

            project.getTasks().register(taskName, GeneratePatchTask.class, task -> {
                task.setGroup(TASK_GROUP);
                task.setDescription("Generate patch for " + variantName + " variant");
                
                // 设置任务属性
                task.getBaselineApk().set(extension.getBaselineApk());
                task.getOutputDir().set(extension.getOutputDir());
                task.getEngine().set(extension.getEngine());
                task.getPatchMode().set(extension.getPatchMode());
                task.getPatchEnabled().set(extension.isEnabled());
                
                // 设置签名配置
                if (extension.getSigning() != null) {
                    task.getKeystoreFile().set(extension.getSigning().getKeystoreFile());
                    task.getKeystorePassword().set(extension.getSigning().getKeystorePassword());
                    task.getKeyAlias().set(extension.getSigning().getKeyAlias());
                    task.getKeyPassword().set(extension.getSigning().getKeyPassword());
                }

                // 尝试获取 variant 的输出 APK
                try {
                    java.lang.reflect.Method getOutputsMethod = variant.getClass().getMethod("getOutputs");
                    Object outputs = getOutputsMethod.invoke(variant);
                    if (outputs instanceof Iterable) {
                        for (Object output : (Iterable<?>) outputs) {
                            java.lang.reflect.Method getOutputFileMethod = output.getClass()
                                    .getMethod("getOutputFile");
                            Object outputFile = getOutputFileMethod.invoke(output);
                            if (outputFile instanceof java.io.File) {
                                task.getNewApk().set((java.io.File) outputFile);
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Could not get variant output file: {}", e.getMessage());
                }

                // 依赖 assemble 任务
                try {
                    java.lang.reflect.Method getAssembleProviderMethod = variant.getClass()
                            .getMethod("getAssembleProvider");
                    Object assembleProvider = getAssembleProviderMethod.invoke(variant);
                    task.dependsOn(assembleProvider);
                } catch (Exception e) {
                    // 尝试使用任务名称依赖
                    String assembleTaskName = "assemble" + capitalize(variantName);
                    if (project.getTasks().findByName(assembleTaskName) != null) {
                        task.dependsOn(assembleTaskName);
                    }
                }
            });

            logger.info("Registered task: {}", taskName);
            
        } catch (Exception e) {
            logger.warn("Failed to register variant task: {}", e.getMessage());
        }
    }

    /**
     * 注册通用任务（非 Android 项目）
     */
    private void registerGenericTask(Project project, PatchExtension extension) {
        String taskName = "generatePatch";
        
        // 检查任务是否已存在
        if (project.getTasks().findByName(taskName) != null) {
            return;
        }

        project.getTasks().register(taskName, GeneratePatchTask.class, task -> {
            task.setGroup(TASK_GROUP);
            task.setDescription("Generate patch from baseline APK to new APK");
            
            // 设置任务属性
            task.getBaselineApk().set(extension.getBaselineApk());
            task.getNewApk().set(extension.getNewApk());
            task.getOutputDir().set(extension.getOutputDir());
            task.getEngine().set(extension.getEngine());
            task.getPatchMode().set(extension.getPatchMode());
            task.getPatchEnabled().set(extension.isEnabled());
            
            // 设置签名配置
            if (extension.getSigning() != null) {
                task.getKeystoreFile().set(extension.getSigning().getKeystoreFile());
                task.getKeystorePassword().set(extension.getSigning().getKeystorePassword());
                task.getKeyAlias().set(extension.getSigning().getKeyAlias());
                task.getKeyPassword().set(extension.getSigning().getKeyPassword());
            }
        });

        project.getLogger().info("Registered task: {}", taskName);
    }

    /**
     * 首字母大写
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
