package com.mjh.shizukufix.hooks

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import com.mjh.shizukufix.XposedLoader
import com.mjh.shizukufix.models.ShizukuFixConfig
import com.mjh.shizukufix.utils.LogX
import com.mjh.shizukufix.utils.PackageHelper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Method

/**
 * Path B: 向 Shizuku 授权列表注入 Scene
 *
 * 原 shizuku/ShizukuListInjector.java 转 Kotlin。
 *
 * 多策略注入（任何一种成功即可让 Scene 出现在 Shizuku 授权列表中）：
 *  1. Hook ApplicationPackageManager.getInstalledApplications / getInstalledPackages
 *     在返回结果中追加 Scene
 *  2. Hook RecyclerView.Adapter.getItemCount（UI 层数据观察）
 *  3. Hook Shizuku API 类 (rikka.shizuku.Shizuku / moe.shizuku.api.ShizukuApi 等)
 *     中的 getApplication 系列 / getPackageList / queryApps 等列表型方法
 *  4. Hook Shizuku PermissionManager 任何返回 List/Map/Set 的方法
 *  5. Hook androidx.recyclerview.widget.ListAdapter.submitList 注入数据
 *
 * 设计要点：
 *  - sSceneInjected 标记一次进程内只注入一次，避免重复
 *  - extractPackageName 兼容 ApplicationInfo / PackageInfo / ResolveInfo / 反射 getPackageName
 */
object ShizukuListInjectorHook {

    /** Shizuku API 候选类名（覆盖多个变体） */
    private val SHIZUKU_API_CLASSES = arrayOf(
        "rikka.shizuku.Shizuku",
        "rikka.shizuku.ShizukuProvider",
        "moe.shizuku.api.ShizukuApi",
        "moe.shizuku.server.api.ServerApi",
        "rikka.shizuku.server.api.ServerApi",
        "moe.shizuku.manager.ShizukuManager"
    )

    /** Shizuku API 列表型方法名候选 */
    private val SHIZUKU_LIST_METHODS = arrayOf(
        "getApplication", "getApplications", "getInstalledApps",
        "getPackageList", "getAppList", "queryApps",
        "getPermissionRequests", "getPendingRequests", "getRequestedApps"
    )

    /** Shizuku PermissionManager 候选类名 */
    private val PERMISSION_CLASSES = arrayOf(
        "rikka.shizuku.manager.permission.PermissionManager",
        "moe.shizuku.manager.permission.PermissionManager",
        "rikka.shizuku.manager.permission.AppPermission",
        "moe.shizuku.manager.PermissionHelper",
        "rikka.shizuku.manager.PermissionHelper"
    )

    /** 进程级单次注入标记 */
    private val sceneInjected = java.util.concurrent.atomic.AtomicBoolean(false)

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: ShizukuFixConfig) {
        if (!cfg.listInjectorEnabled) return
        LogX.i("Path B: 向 Shizuku 授权列表注入 Scene 启动")

        hookPackageManager(lpparam)
        hookRecyclerViewAdapter(lpparam)
        hookShizukuApiMethods(lpparam)
        hookPermissionManager(lpparam)
        hookListAdapterSetData(lpparam)
    }

    // ============ 策略1: Hook ApplicationPackageManager ============
    private fun hookPackageManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pmCls = XposedHelpers.findClassIfExists(
            "android.app.ApplicationPackageManager", lpparam.classLoader) ?: run {
            LogX.e("ApplicationPackageManager not found")
            return
        }

        // getInstalledApplications(int)
        try {
            val m = pmCls.getDeclaredMethod("getInstalledApplications", Int::class.javaPrimitiveType)
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val result = p.result as? List<*> ?: return
                    if (result.isEmpty()) return
                    if (sceneInjected.get()) return
                    if (result.any { (it as? ApplicationInfo)?.packageName == XposedLoader.SCENE_PACKAGE }) return
                    val ctx = getContext(lpparam) ?: return
                    val sceneInfo = PackageHelper.getApplicationInfo(ctx, XposedLoader.SCENE_PACKAGE) ?: return
                    val newList = ArrayList(result)
                    newList.add(sceneInfo)
                    p.result = newList
                    sceneInjected.set(true)
                    LogX.i("  [PM] Injected Scene into getInstalledApplications()")
                }
            })
            LogX.hookSuccess("ApplicationPackageManager", "getInstalledApplications")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        // getInstalledPackages(int)
        try {
            val m = pmCls.getDeclaredMethod("getInstalledPackages", Int::class.javaPrimitiveType)
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val result = p.result as? List<*> ?: return
                    if (result.isEmpty()) return
                    if (sceneInjected.get()) return
                    if (result.any { (it as? PackageInfo)?.packageName == XposedLoader.SCENE_PACKAGE }) return
                    val ctx = getContext(lpparam) ?: return
                    val scenePkg = PackageHelper.getPackageInfo(ctx, XposedLoader.SCENE_PACKAGE) ?: return
                    val newList = ArrayList(result)
                    newList.add(scenePkg)
                    p.result = newList
                    sceneInjected.set(true)
                    LogX.i("  [PM] Injected Scene into getInstalledPackages()")
                }
            })
            LogX.hookSuccess("ApplicationPackageManager", "getInstalledPackages")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        // queryIntentActivities(Intent, int)
        try {
            val m = pmCls.getDeclaredMethod("queryIntentActivities",
                android.content.Intent::class.java, Int::class.javaPrimitiveType)
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val result = p.result as? List<*> ?: return
                    if (result.isEmpty()) return
                    val hasShizuku = result.any {
                        val ai = (it as? ResolveInfo)?.activityInfo
                        ai?.packageName?.lowercase()?.contains("shizuku") == true
                    }
                    if (hasShizuku && !sceneInjected.get()) {
                        LogX.d("  [PM] queryIntentActivities returned Shizuku-related results")
                    }
                }
            })
            LogX.hookSuccess("ApplicationPackageManager", "queryIntentActivities")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    // ============ 策略2: Hook RecyclerView.Adapter.getItemCount ============
    private fun hookRecyclerViewAdapter(lpparam: XC_LoadPackage.LoadPackageParam) {
        val adapter = XposedHelpers.findClassIfExists(
            "androidx.recyclerview.widget.RecyclerView\$Adapter", lpparam.classLoader) ?: return
        try {
            XposedHelpers.findAndHookMethod(adapter, "getItemCount", object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val count = p.result as? Int ?: return
                    if (count >= 0 && !sceneInjected.get()) {
                        // 仅观察，实际注入由其他策略完成
                    }
                }
            })
            LogX.hookSuccess("RecyclerView.Adapter", "getItemCount")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    // ============ 策略3: Hook Shizuku API 列表型方法 ============
    private fun hookShizukuApiMethods(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (className in SHIZUKU_API_CLASSES) {
            val clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader) ?: continue
            LogX.i("  Processing Shizuku class: $className")
            for (methodName in SHIZUKU_LIST_METHODS) {
                tryHookMethod(clazz, methodName, lpparam)
            }
        }
    }

    private fun tryHookMethod(clazz: Class<*>, methodName: String, lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val methods = clazz.declaredMethods.filter { it.name == methodName }
            if (methods.isEmpty()) return
            for (m in methods) {
                XposedBridge.hookMethod(m, createListInjectionHook(methodName, lpparam))
                LogX.i("    Hooked: ${clazz.name}.$methodName()")
            }
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
    }

    private fun createListInjectionHook(methodName: String, lpparam: XC_LoadPackage.LoadPackageParam): XC_MethodHook {
        return object : XC_MethodHook() {
            override fun afterHookedMethod(p: XC_MethodHook.MethodHookParam) {
                val result = p.result ?: return
                try {
                    when (result) {
                        is List<*> -> injectSceneIntoList(result, lpparam, methodName)
                        is Array<*> -> {
                            val list = ArrayList(result.toList())
                            if (injectSceneIntoList(list, lpparam, methodName)) {
                                p.result = list.toArray()
                            }
                        }
                    }
                } catch (t: Throwable) {
                    LogX.e("    Error in list injection hook", t)
                }
            }
        }
    }

    private fun injectSceneIntoList(list: List<*>, lpparam: XC_LoadPackage.LoadPackageParam, methodName: String): Boolean {
        try {
            if (list.any { extractPackageName(it) == XposedLoader.SCENE_PACKAGE }) return false
            val ctx = getContext(lpparam) ?: return false
            val sceneInfo = PackageHelper.getApplicationInfo(ctx, XposedLoader.SCENE_PACKAGE) ?: return false
            @Suppress("UNCHECKED_CAST")
            val mutable = list as? MutableList<Any?>
            mutable?.add(0, sceneInfo)
            sceneInjected.set(true)
            LogX.i("    Injected Scene into list from method: $methodName")
            return true
        } catch (t: Throwable) {
            LogX.e("    Error injecting Scene", t)
            return false
        }
    }

    /** 兼容多种类型反射取包名 */
    private fun extractPackageName(obj: Any?): String? {
        if (obj == null) return null
        when (obj) {
            is String -> return obj
            is ApplicationInfo -> return obj.packageName
            is PackageInfo -> return obj.packageName
            is ResolveInfo -> {
                obj.activityInfo?.packageName?.let { return it }
                obj.serviceInfo?.packageName?.let { return it }
                obj.providerInfo?.packageName?.let { return it }
            }
        }
        // 反射 getPackageName()
        try {
            val m = obj.javaClass.getMethod("getPackageName")
            return m.invoke(obj) as? String
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        // 反射 packageName 字段
        try {
            val f = obj.javaClass.getDeclaredField("packageName")
            f.isAccessible = true
            return f.get(obj)?.toString()
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        return null
    }

    // ============ 策略4: Hook PermissionManager 任何返回集合的方法 ============
    private fun hookPermissionManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        for (className in PERMISSION_CLASSES) {
            val clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader) ?: continue
            LogX.i("  Found permission class: $className")
            for (m in clazz.declaredMethods) {
                val rt = m.returnType
                if (java.util.List::class.java.isAssignableFrom(rt) ||
                    rt.isArray ||
                    rt == java.util.Map::class.java ||
                    rt == java.util.Set::class.java) {
                    try {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun afterHookedMethod(p: XC_MethodHook.MethodHookParam) {
                                try { injectSceneIntoReturn(p) } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                            }
                        })
                        LogX.i("    Hooked: ${m.name}")
                    } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                }
            }
        }
    }

    private fun injectSceneIntoReturn(p: XC_MethodHook.MethodHookParam) {
        val result = p.result ?: return
        when (result) {
            is MutableList<*> -> {
                if (result.any { XposedLoader.SCENE_PACKAGE == it?.toString() }) return
                @Suppress("UNCHECKED_CAST")
                (result as? MutableList<Any?>)?.add(XposedLoader.SCENE_PACKAGE)
                sceneInjected.set(true)
                LogX.i("    Injected Scene string into list")
            }
            is java.util.Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                val map = result as? MutableMap<Any?, Any?> ?: return
                if (!map.containsKey(XposedLoader.SCENE_PACKAGE)) {
                    map[XposedLoader.SCENE_PACKAGE] = XposedLoader.SCENE_PACKAGE
                    sceneInjected.set(true)
                    LogX.i("    Injected Scene into Map result")
                }
            }
            is java.util.Set<*> -> {
                if (!result.contains(XposedLoader.SCENE_PACKAGE)) {
                    @Suppress("UNCHECKED_CAST")
                    (result as? MutableSet<Any?>)?.add(XposedLoader.SCENE_PACKAGE)
                    sceneInjected.set(true)
                    LogX.i("    Injected Scene into Set result")
                }
            }
        }
    }

    // ============ 策略5: Hook androidx ListAdapter.submitList ============
    private fun hookListAdapterSetData(lpparam: XC_LoadPackage.LoadPackageParam) {
        // ListAdapter.submitList(List)
        val listAdapter = XposedHelpers.findClassIfExists(
            "androidx.recyclerview.widget.ListAdapter", lpparam.classLoader) ?: return
        try {
            val m: Method = listAdapter.getDeclaredMethod("submitList", java.util.List::class.java)
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    try {
                        val list = p.args[0] as? List<*> ?: return
                        if (list.isEmpty() || sceneInjected.get()) return
                        ensureSceneInAdapterData(list, lpparam)
                    } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
                }
            })
            LogX.hookSuccess("ListAdapter", "submitList")
        } catch (e: Throwable) { LogX.w("异常: ${e.message}") }

        // ArrayAdapter.addAll / add / clear（旧版 ListView）
        val arrayAdapter = XposedHelpers.findClassIfExists(
            "android.widget.ArrayAdapter", lpparam.classLoader) ?: return
        for (name in listOf("addAll", "add", "clear")) {
            try {
                for (m in arrayAdapter.declaredMethods) {
                    if (m.name == name) {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {})
                    }
                }
            } catch (e: Throwable) { LogX.w("异常: ${e.message}") }
        }
    }

    private fun ensureSceneInAdapterData(list: List<*>, lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            if (list.isEmpty()) return
            if (list.any { extractPackageName(it) == XposedLoader.SCENE_PACKAGE }) return
            val ctx = getContext(lpparam) ?: return
            val sceneInfo = PackageHelper.getApplicationInfo(ctx, XposedLoader.SCENE_PACKAGE) ?: return
            (list as? MutableList<Any?>)?.add(0, sceneInfo)
            sceneInjected.set(true)
            LogX.i("  [Adapter] Injected Scene into adapter data list")
        } catch (t: Throwable) {
            LogX.e("  [Adapter] Error ensuring Scene in data", t)
        }
    }

    // ============ 工具：通过 ActivityThread 获取 Context ============
    private fun getContext(lpparam: XC_LoadPackage.LoadPackageParam): Context? {
        return try {
            val at = XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader)
            val cat = XposedHelpers.callStaticMethod(at, "currentActivityThread")
            XposedHelpers.callMethod(cat, "getApplication") as? Context
        } catch (t: Throwable) {
            LogX.e("  Failed to get Context via ActivityThread", t)
            null
        }
    }
}
