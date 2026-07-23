package com.stepmod.noroot.hooks

import com.stepmod.noroot.models.StepConfig
import com.stepmod.noroot.utils.LogStore
import com.stepmod.noroot.utils.LogX
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

object GpxRouteInjectHook {

    private var waypoints = listOf<LatLng>()
    private var wpIndex = 0
    private var startTimeMs = 0L
    private var totalElapsed = 0.0

    data class LatLng(val lat: Double, val lng: Double)

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: StepConfig) {
        if (!cfg.gpxRouteEnabled) return

        loadGpxData(cfg)
        if (waypoints.isEmpty()) {
            LogX.w("GPX航点为空，跳过注入")
            return
        }

        LogX.i("GPX路线注入 Hook 启动 | 航点数=${waypoints.size} 回放倍速=${cfg.gpxPlaybackSpeed}")
        try { LogStore.add("gpx", "GPX路线注入：${waypoints.size}个航点") } catch (_: Exception) { }

        startTimeMs = System.currentTimeMillis()
        hookLocationManager(lpparam, cfg)
    }

    private fun loadGpxData(cfg: StepConfig) {
        val path = cfg.gpxFilePath
        if (path.isBlank()) {
            waypoints = generateDefaultTrack()
            return
        }

        try {
            val file = java.io.File(path)
            if (!file.exists()) {
                LogX.w("GPX文件不存在: $path，使用默认路线")
                waypoints = generateDefaultTrack()
                return
            }

            val xml = file.readText()
            waypoints = parseGpx(xml)
            if (waypoints.isEmpty()) {
                waypoints = generateDefaultTrack()
            }
        } catch (e: Exception) {
            LogX.w("GPX解析失败: ${e.message}，使用默认路线")
            waypoints = generateDefaultTrack()
        }
    }

    private fun parseGpx(xml: String): List<LatLng> {
        val points = mutableListOf<LatLng>()
        val trkptRegex = """<trkpt\s+lat="([^"]+)"\s+lon="([^"]+)"\s*>""".toRegex()
        val matches = trkptRegex.findAll(xml)
        for (m in matches) {
            try {
                val lat = m.groupValues[1].toDouble()
                val lng = m.groupValues[2].toDouble()
                points.add(LatLng(lat, lng))
            } catch (_: Exception) { }
        }
        return points
    }

    private fun generateDefaultTrack(): List<LatLng> {
        return listOf(
            LatLng(39.9042, 116.4074), LatLng(39.9052, 116.4084), LatLng(39.9062, 116.4094),
            LatLng(39.9072, 116.4104), LatLng(39.9082, 116.4114), LatLng(39.9092, 116.4124),
            LatLng(39.9102, 116.4134), LatLng(39.9112, 116.4144), LatLng(39.9122, 116.4154),
            LatLng(39.9132, 116.4164), LatLng(39.9142, 116.4174), LatLng(39.9152, 116.4184),
            LatLng(39.9162, 116.4194), LatLng(39.9172, 116.4204), LatLng(39.9182, 116.4214),
            LatLng(39.9192, 116.4224), LatLng(39.9202, 116.4234), LatLng(39.9212, 116.4244),
            LatLng(39.9222, 116.4254), LatLng(39.9232, 116.4264)
        )
    }

    private fun getCurrentWaypoint(speed: Float): LatLng? {
        if (waypoints.isEmpty()) return null
        val elapsed = (System.currentTimeMillis() - startTimeMs) * speed / 1000.0
        val stepInterval = 5.0
        wpIndex = ((elapsed / stepInterval).toInt()) % waypoints.size
        return waypoints[wpIndex]
    }

    private fun hookLocationManager(
        lpparam: XC_LoadPackage.LoadPackageParam,
        cfg: StepConfig
    ) {
        try {
            val lmCls = XposedHelpers.findClassIfExists(
                "android.location.LocationManager", lpparam.classLoader)
            if (lmCls != null) {
                hookRequestLocationUpdates(lmCls, cfg)
                hookGetLastKnownLocation(lmCls, cfg)
            }
        } catch (e: Exception) {
            LogX.w("LocationManager未找到: ${e.message}")
        }

        try {
            val fusedCls = XposedHelpers.findClassIfExists(
                "com.google.android.gms.location.FusedLocationProviderClient", lpparam.classLoader)
            if (fusedCls != null) {
                hookFusedLocation(fusedCls, cfg)
            }
        } catch (e: Exception) { LogX.w("FusedLocation未找到: ${e.message}") }
    }

    private fun hookRequestLocationUpdates(
        lmCls: Class<*>,
        cfg: StepConfig
    ) {
        try {
            XposedHelpers.findAndHookMethod(lmCls, "requestLocationUpdates",
                String::class.java, Long::class.javaPrimitiveType,
                Float::class.javaPrimitiveType, "android.location.LocationListener",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        LogX.d("LocationManager.requestLocationUpdates 已拦截")
                    }
                })
            LogX.hookSuccess("GPX", "LocationManager.requestLocationUpdates")
        } catch (e: Exception) { LogX.w("requestLocationUpdates Hook失败: ${e.message}") }
    }

    private fun hookGetLastKnownLocation(
        lmCls: Class<*>,
        cfg: StepConfig
    ) {
        try {
            XposedHelpers.findAndHookMethod(lmCls, "getLastKnownLocation",
                String::class.java, object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val wp = getCurrentWaypoint(cfg.gpxPlaybackSpeed) ?: return
                            p.result = buildLocation(wp)
                            LogX.d("注入GPX位置: ${wp.lat}, ${wp.lng}")
                        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("GPX", "LocationManager.getLastKnownLocation")
        } catch (e: Exception) { LogX.w("getLastKnownLocation Hook失败: ${e.message}") }
    }

    private fun hookFusedLocation(
        fusedCls: Class<*>,
        cfg: StepConfig
    ) {
        try {
            XposedHelpers.findAndHookMethod(fusedCls, "getLastLocation",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam) {
                        try {
                            val wp = getCurrentWaypoint(cfg.gpxPlaybackSpeed) ?: return
                            val location = buildLocation(wp)
                            val taskClass = XposedHelpers.findClassIfExists(
                                "com.google.android.gms.tasks.Tasks", fusedCls.classLoader)
                            if (taskClass != null) {
                                p.result = taskClass.getMethod("forResult", Object::class.java)
                                    .invoke(null, location)
                            }
                        } catch (e: Exception) { LogX.w("异常: ${e.message}") }
                    }
                })
            LogX.hookSuccess("GPX", "FusedLocation.getLastLocation")
        } catch (e: Exception) { LogX.w("FusedLocation Hook失败: ${e.message}") }
    }

    private fun buildLocation(wp: LatLng): Any? {
        return try {
            val locCls = Class.forName("android.location.Location")
            val location = locCls.getConstructor(String::class.java)
                .newInstance("gpx")
            val setLatMethod = locCls.getMethod("setLatitude", Double::class.javaPrimitiveType)
            val setLngMethod = locCls.getMethod("setLongitude", Double::class.javaPrimitiveType)
            val setTimeMethod = locCls.getMethod("setTime", Long::class.javaPrimitiveType)
            val setAccMethod = locCls.getMethod("setAccuracy", Float::class.javaPrimitiveType)

            setLatMethod.invoke(location, wp.lat)
            setLngMethod.invoke(location, wp.lng)
            setTimeMethod.invoke(location, System.currentTimeMillis())
            setAccMethod.invoke(location, 5.0f)

            location
        } catch (e: Exception) {
            LogX.w("构建Location失败: ${e.message}")
            null
        }
    }
}
