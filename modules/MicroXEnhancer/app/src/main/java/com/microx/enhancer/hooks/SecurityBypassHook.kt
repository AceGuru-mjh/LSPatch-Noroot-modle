package com.microx.enhancer.hooks

import com.microx.enhancer.utils.ConfigManager
import com.microx.enhancer.utils.HookHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 安全检测绕过Hook类
 *
 * 核心目标：绕过微信/QQ对Xposed、LSPatch、LSPatch进程的检测
 *
 * 微信/QQ安全检测的常见手段：
 * 1. 检查XposedBridge类是否存在
 * 2. 检查/system/framework/XposedBridge.jar（Xposed框架文件）
 * 3. 检查已加载的ClassLoader中是否有Xposed相关类
 * 4. 检查进程的maps文件中是否有Xposed so库
 * 5. 扫描/sdcard/Android/data/下的LSPatch目录
 * 6. 检查/system/lib/libxposed_art.so等文件
 * 7. 反射调用XposedHelpers.findClass来检测是否能找到特殊类
 * 8. 调用ClassLoader.loadClass("de.robv.android.xposed.XposedBridge")看是否抛异常
 *
 * 绕过策略（仅在应用进程内存内，不涉及系统层）：
 * 1. Hook安全检测方法，修改返回值为"安全/未检测到"
 * 2. Hook文件检测方法，对检查Xposed框架文件路径返回false
 * 3. Hook反射检测，对findClass等调用返回ClassNotFoundException
 * 4. 隐藏LSPatch注入痕迹
 */
object SecurityBypassHook {

    // ===== 安全检测绕过（微信+QQ通用） =====
    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        HookHelper.log("加载安全检测绕过Hook")

        // 1. 绕过Xposed类检测
        bypassXposedClassCheck(lpparam)

        // 2. 绕过文件系统检测
        bypassFileCheck(lpparam)

        // 3. 绕过堆栈检测
        bypassStackTraceCheck(lpparam)

        // 4. 绕过LSPatch特征检测
        bypassLSPatchCheck(lpparam)

        // 5. 隐藏Xposed框架特征
        hideXposedFeatures(lpparam)
    }

    // ================================================================
    //  1. 绕过Xposed类检测
    //  微信/QQ通常通过以下方式检测：
    //  - Class.forName("de.robv.android.xposed.XposedBridge")
    //  - ClassLoader.loadClass("de.robv.android.xposed.XposedBridge")
    //  - 反射调用XposedHelpers.findXXX
    // ================================================================
    private fun bypassXposedClassCheck(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook Class.forName：对Xposed相关类名抛出ClassNotFoundException
        val classClass = XposedHelpers.findClass("java.lang.Class", lpparam.classLoader)

        HookHelper.hookAllMethodsSafe(classClass, "forName", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val className = param.args.getOrNull(0) as? String ?: ""
                    
                                // 检测到Xposed相关类名时，模拟ClassNotFoundException
                                if (isXposedClassName(className)) {
                                    HookHelper.log("[安全绕过] 拦截Class.forName检测: $className")
                                    throw ClassNotFoundException(className)
                                }
                }
            })

        // Hook ClassLoader.loadClass
        val classLoaderClass = XposedHelpers.findClass(
            "java.lang.ClassLoader", lpparam.classLoader
        )

        HookHelper.hookAllMethodsSafe(classLoaderClass, "loadClass", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val className = param.args.getOrNull(0) as? String ?: ""
                    
                                if (isXposedClassName(className)) {
                                    HookHelper.log("[安全绕过] 拦截ClassLoader.loadClass检测: $className")
                                    throw ClassNotFoundException(className)
                                }
                }
            })

        // Hook反射获取方法列表：过滤掉Xposed注入的方法
        hookReflectionChecks(lpparam)
    }

    /** Hook反射相关检测 */
    private fun hookReflectionChecks(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook getDeclaredMethods — 微信可能通过此方法检查是否有Xposed注入的额外方法
        val classClass2 = XposedHelpers.findClass("java.lang.Class", lpparam.classLoader)

        HookHelper.hookAllMethodsSafe(classClass2, "getDeclaredMethods", object : XC_MethodHook() {
                override fun afterHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val result = param.result as? Array<*>
                    
                                if (result != null && result.isNotEmpty()) {
                                    // 检查被查询的类是否是微信自身的关键类
                                    val callerClassName = getCallerClassName()
                    
                                    if (callerClassName != null &&
                                        (callerClassName.contains("com.tencent.mm") ||
                                                callerClassName.contains("com.tencent.mobileqq"))
                                    ) {
                                        // 过滤掉由Xposed注入的额外方法
                                        val filtered = result.filter { method ->
                                            try {
                                                val mn = (method as? java.lang.reflect.Method)?.name ?: return@filter true
                                                !mn.startsWith("xposed") && !mn.contains("XC_") && !mn.contains("hook")
                                            } catch (e: Exception) {
                                                true
                                            }
                                        }
                                        if (filtered.size < result.size) {
                                            param.result = filtered.toTypedArray()
                                        }
                                    }
                                }
                }
            })

        // Hook getDeclaredField — 防止检测Xposed注入的字段
        HookHelper.hookAllMethodsSafe(classClass2, "getDeclaredField", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val fieldName = param.args.getOrNull(0) as? String ?: ""
                                if (fieldName.contains("xposed") || fieldName.contains("Xposed")) {
                                    HookHelper.log("[安全绕过] 拦截Xposed字段查询: $fieldName")
                                    throw NoSuchFieldException(fieldName)
                                }
                }
            })
    }

    // ================================================================
    //  2. 绕过文件系统检测
    //  微信/QQ检测以下路径来判断是否有Xposed/LSPatch：
    //  - /system/framework/XposedBridge.jar
    //  - /system/lib/libxposed_art.so
    //  - /system/lib64/libxposed_art.so
    //  - /data/data/de.robv.android.xposed.installer/
    //  - /data/local/tmp/ (LSPatch可能存放临时文件)
    //  通过Hook File.exists()、File.isFile()等方法来拦截
    // ================================================================
    private fun bypassFileCheck(lpparam: XC_LoadPackage.LoadPackageParam) {
        val fileClass = XposedHelpers.findClass("java.io.File", lpparam.classLoader)

        // 需要拦截的特征文件路径
        val blockedPaths = listOf(
            "XposedBridge.jar",
            "libxposed_art.so",
            "libxposed",
            "xposed.prop",
            "xposedbridge",
            "xposed_",
            "lspatch",
            "lsposed",
            "lsp_",
        )

        // Hook File.exists()
        HookHelper.hookAllMethodsSafe(fileClass, "exists", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val filePath = param.thisObject.toString().lowercase()
                    
                                if (blockedPaths.any { filePath.contains(it) }) {
                                    HookHelper.log("[安全绕过] 拦截文件检测: $filePath")
                                    param.result = false // 文件不存在
                                }
                }
            })

        // Hook File.isFile()
        HookHelper.hookAllMethodsSafe(fileClass, "isFile", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val filePath = param.thisObject.toString().lowercase()
                                if (blockedPaths.any { filePath.contains(it) }) {
                                    param.result = false
                                }
                }
            })

        // Hook File.canRead() — 某些检测会尝试读取框架文件
        HookHelper.hookAllMethodsSafe(fileClass, "canRead", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val filePath = param.thisObject.toString().lowercase()
                                if (blockedPaths.any { filePath.contains(it) }) {
                                    param.result = false
                                }
                }
            })

        // Hook File.length() — 检验文件是否非空
        HookHelper.hookAllMethodsSafe(fileClass, "length", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val filePath = param.thisObject.toString().lowercase()
                                if (blockedPaths.any { filePath.contains(it) }) {
                                    param.result = 0L // 返回0长度
                                }
                }
            })

        // Hook Runtime.exec — 防止通过shell命令检测
        hookRuntimeExec(lpparam, blockedPaths)
    }

    /** Hook Runtime.exec / ProcessBuilder 防止通过shell命令检测 */
    private fun hookRuntimeExec(
        lpparam: XC_LoadPackage.LoadPackageParam,
        blockedPaths: List<String>
    ) {
        val runtimeClass = XposedHelpers.findClass("java.lang.Runtime", lpparam.classLoader)

        HookHelper.hookAllMethodsSafe(runtimeClass, "exec", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val command = param.args.getOrNull(0)?.toString()?.lowercase() ?: ""
                    
                                val suspiciousCommands = listOf(
                                    "grep xposed", "grep lspatch", "grep lsposed",
                                    "cat /proc", "ls /system/framework",
                                    "ls /system/lib", "pm list packages",
                                    "dumpsys package", "cat /data/data",
                                )
                    
                                if (suspiciousCommands.any { command.contains(it) }) {
                                    HookHelper.log("[安全绕过] 拦截可疑shell命令: ${command.take(100)}")
                                    // 返回一个假的Process：执行结果为空
                                    // 此处简化处理，直接抛出异常阻止执行
                                    throw SecurityException("Permission denied")
                                }
                }
            })

        // Hook ProcessBuilder
        val pbClass = HookHelper.findClassSafe(lpparam,
            "java.lang.ProcessBuilder"
        )
        if (pbClass != null) {
            HookHelper.hookAllMethodsSafe(pbClass, "start", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val command = try {
                                        val cmdList = param.thisObject.javaClass
                                            .getMethod("command")
                                            .invoke(param.thisObject) as? List<*>
                                        cmdList?.joinToString(" ")?.lowercase() ?: ""
                                    } catch (e: Exception) {
                                        ""
                                    }
                    
                                    if (suspiciousCommand(command)) {
                                        HookHelper.log("[安全绕过] 拦截ProcessBuilder: ${command.take(100)}")
                                        throw SecurityException("Permission denied")
                                    }
                }
            })
        }
    }

    // ================================================================
    //  3. 绕过堆栈检测
    //  微信/QQ可能通过以下方式检测Xposed：
    //  - StackTraceElement中查找de.robv.android.xposed包名
    //  - 检测调用栈中是否有Xposed的方法
    //  策略：Hook获取调用栈的方法，过滤Xposed相关元素
    // ================================================================
    private fun bypassStackTraceCheck(lpparam: XC_LoadPackage.LoadPackageParam) {
        val threadClass = XposedHelpers.findClass("java.lang.Thread", lpparam.classLoader)
        val throwableClass = XposedHelpers.findClass("java.lang.Throwable", lpparam.classLoader)

        // Hook Thread.getStackTrace()
        HookHelper.hookAllMethodsSafe(threadClass, "getStackTrace", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val original = param.result as? Array<StackTraceElement>
                                if (original != null) {
                                    val filtered = original.filter { element ->
                                        val className = element.className
                                        // 过滤掉Xposed/LSPatch相关的堆栈信息
                                        !className.contains("de.robv.android.xposed") &&
                                                !className.contains("org.lsposed") &&
                                                !className.contains("lspatch") &&
                                                !className.contains("XposedBridge")
                                    }
                                    if (filtered.size < original.size) {
                                        HookHelper.logD("[安全绕过] 过滤堆栈: ${original.size} -> ${filtered.size}")
                                        param.result = filtered.toTypedArray()
                                    }
                                }
                }
            })

        // Hook Throwable.getStackTrace()
        HookHelper.hookAllMethodsSafe(throwableClass, "getStackTrace", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val original = param.result as? Array<StackTraceElement>
                                if (original != null) {
                                    val filtered = original.filter { element ->
                                        !element.className.contains("de.robv.android.xposed") &&
                                                !element.className.contains("org.lsposed") &&
                                                !element.className.contains("lspatch")
                                    }
                                    if (filtered.size < original.size) {
                                        param.result = filtered.toTypedArray()
                                    }
                                }
                }
            })

        // Hook Security.getStackTrace() — 某些安全框架使用
        try {
            val securityClass = HookHelper.findClassSafe(lpparam,
                "java.lang.SecurityManager"
            )
            if (securityClass != null) {
                HookHelper.hookAllMethodsSafe(securityClass, "getClassContext", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val original = param.result as? Array<Class<*>>
                                        if (original != null) {
                                            val filtered = original.filter { clazz ->
                                                !clazz.name.contains("de.robv.android.xposed") &&
                                                        !clazz.name.contains("org.lsposed")
                                            }
                                            if (filtered.size < original.size) {
                                                param.result = filtered.toTypedArray()
                                            }
                                        }
                }
            })
            }
        } catch (e: Exception) {
            // 忽略
        }
    }

    // ================================================================
    //  4. 绕过LSPatch特征检测
    //  LSPatch的特征：
    //  - /data/local/tmp/lspatch/
    //  - org.lsposed.lspatch 包名的残留
    //  - AndroidManifest中的meta-data
    // ================================================================
    private fun bypassLSPatchCheck(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook ApplicationInfo获取 — 防止检测到LSPatch的meta-data
        val pmClass = HookHelper.findClassSafe(lpparam,
            "android.content.pm.PackageManager"
        )
        if (pmClass != null) {
            HookHelper.hookAllMethodsSafe(pmClass, "getApplicationInfo", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    // 不拦截正常获取，但过滤meta-data中的Xposed标记
                                    // 这在大部分情况下不需要（LSPatch本身已经处理）
                }
            })

            // Hook PackageManager.getInstalledPackages/getInstalledApplications
            // 防止微信扫描已安装应用列表检测Xposed/LSPatch
            HookHelper.hookAllMethodsSafe(pmClass, "getInstalledPackages", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val result = param.result as? List<*>
                                    if (result != null) {
                                        // 过滤掉Xposed/LSPatch相关的包
                                        val filtered = result.filter { pkgInfo ->
                                            try {
                                                val pkgName = pkgInfo?.javaClass
                                                    ?.getField("packageName")
                                                    ?.get(pkgInfo) as? String ?: ""
                                                !pkgName.contains("xposed") &&
                                                        !pkgName.contains("lsposed") &&
                                                        !pkgName.contains("lspatch") &&
                                                        pkgName != "de.robv.android.xposed.installer" &&
                                                        pkgName != "org.meowcat.edxposed.manager"
                                            } catch (e: Exception) {
                                                true
                                            }
                                        }
                                        if (filtered.size < result.size) {
                                            HookHelper.log("[安全绕过] 过滤包列表: ${result.size} -> ${filtered.size}")
                                            param.result = filtered
                                        }
                                    }
                }
            })

            HookHelper.hookAllMethodsSafe(pmClass, "getInstalledApplications", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val result = param.result as? List<*>
                                    if (result != null) {
                                        val filtered = result.filter { appInfo ->
                                            try {
                                                val pkgName = appInfo?.javaClass
                                                    ?.getField("packageName")
                                                    ?.get(appInfo) as? String ?: ""
                                                !pkgName.contains("xposed") &&
                                                        !pkgName.contains("lsposed") &&
                                                        !pkgName.contains("lspatch")
                                            } catch (e: Exception) {
                                                true
                                            }
                                        }
                                        if (filtered.size < result.size) {
                                            param.result = filtered
                                        }
                                    }
                }
            })
        }
    }

    // ================================================================
    //  5. 隐藏Xposed框架特征
    //  补充一些零散的特征隐藏
    // ================================================================
    private fun hideXposedFeatures(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook System.getProperty — 防止通过系统属性检测
        val systemClass = XposedHelpers.findClass("java.lang.System", lpparam.classLoader)

        HookHelper.hookAllMethodsSafe(systemClass, "getProperty", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val key = param.args.getOrNull(0) as? String ?: ""
                                val blockedKeys = listOf(
                                    "xposed", "lsposed", "lspatch",
                                    "vxp", "edxposed", "ro.product.cpu.abi",
                                )
                    
                                // 移除可能暴露框架存在的结果
                                if (blockedKeys.any { key.lowercase().contains(it) }) {
                                    val originalResult = param.result as? String ?: ""
                                    if (originalResult.isNotEmpty() &&
                                        (originalResult.contains("xposed") ||
                                                originalResult.contains("lsposed"))
                                    ) {
                                        HookHelper.log("[安全绕过] 隐藏系统属性: $key")
                                        param.result = ""
                                    }
                                }
                }
            })

        // Hook /proc/self/maps读取（微信常检查maps文件）
        // 通过Hook FileInputStream/BufferedReader的read方法来过滤
        hookProcMapsRead(lpparam)
    }

    /** Hook /proc/self/maps 文件读取 */
    private fun hookProcMapsRead(lpparam: XC_LoadPackage.LoadPackageParam) {
        val fisClass = HookHelper.findClassSafe(lpparam, "java.io.FileInputStream")
        if (fisClass == null) return

        HookHelper.hookAllMethodsSafe(fisClass, "read", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    // FileInputStream.read()的读取过滤比较复杂
                                // 这里采用更简洁的方案：在前面已经Hook了File.exists等，阻止了文件层面的检测
                                // /proc文件读取的过滤可在此扩展
                }
            })

        // Hook BufferedReader.readLine — 过滤maps文件中的Xposed行
        val brClass = HookHelper.findClassSafe(lpparam, "java.io.BufferedReader")
        if (brClass != null) {
            HookHelper.hookAllMethodsSafe(brClass, "readLine", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val line = param.result as? String
                                    if (line != null) {
                                        val lowerLine = line.lowercase()
                                        if (lowerLine.contains("xposed") ||
                                            lowerLine.contains("lspatch") ||
                                            lowerLine.contains("lsposed")
                                        ) {
                                            HookHelper.log("[安全绕过] 过滤maps行: ${line.take(80)}")
                                            param.result = "" // 返回空行
                                        }
                                    }
                }
            })
        }
    }

    // ================================================================
    //  工具方法
    // ================================================================

    /** 判断类名是否为Xposed相关 */
    private fun isXposedClassName(className: String): Boolean {
        val lowerName = className.lowercase()
        return lowerName.contains("xposed") ||
                lowerName.contains("lsposed") ||
                lowerName.contains("lspatch") ||
                lowerName.contains("edxposed") ||
                lowerName == "de.robv.android.xposed.xposedbridge" ||
                lowerName == "de.robv.android.xposed.xposedhelpers" ||
                lowerName == "de.robv.android.xposed.xposedinit"
    }

    /** 获取调用者的类名（用于判断调用来源） */
    private fun getCallerClassName(): String? {
        return try {
            val stack = Thread.currentThread().stackTrace
            // 跳过当前方法、反射调用等
            for (i in 2 until stack.size) {
                val className = stack[i].className
                if (!className.contains("SecurityBypassHook") &&
                    !className.contains("de.robv.android.xposed") &&
                    !className.contains("java.lang.reflect")
                ) {
                    return className
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** 判断shell命令是否可疑 */
    private fun suspiciousCommand(command: String): Boolean {
        val keywords = listOf(
            "xposed", "lspatch", "lsposed", "/proc",
            "/system/framework", "/system/lib",
            "grep", "cat /proc",
        )
        return keywords.any { command.contains(it) }
    }
}
