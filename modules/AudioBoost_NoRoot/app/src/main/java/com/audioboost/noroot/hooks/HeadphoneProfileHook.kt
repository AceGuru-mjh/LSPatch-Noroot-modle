package com.audioboost.noroot.hooks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.audioboost.noroot.models.AudioConfig
import com.audioboost.noroot.utils.LogX
import com.audioboost.noroot.utils.LogStore
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

object HeadphoneProfileHook {

    private var headphoneConnected = false
    private var btHeadphoneConnected = false
    private var savedBoostLevel: Int? = null
    private var savedBassLevel: Int? = null
    private var savedEqBands: List<Int>? = null

    fun apply(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        if (!cfg.headphoneAutoSwitchEnabled) return
        LogX.i("HeadphoneProfile: 已启用 耳机自动切换 Profile boost=${cfg.headphoneBoostLevel}%")

        try {
            val ctx: Context? = try {
                val at = Class.forName("android.app.ActivityThread")
                    .getMethod("currentActivityThread").invoke(null)
                Class.forName("android.app.ActivityThread")
                    .getMethod("getApplication").invoke(at) as? Context
            } catch (_: Throwable) { null }

            if (ctx != null) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        val action = intent?.action ?: return
                        when (action) {
                            Intent.ACTION_HEADSET_PLUG -> {
                                val state = intent.getIntExtra("state", 0)
                                headphoneConnected = state == 1
                                LogX.i("HeadphoneProfile: 有线耳机 ${if (headphoneConnected) "插入" else "拔出"}")
                                applyHeadphoneProfile(ctx, cfg)
                            }
                            "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED" -> {
                                val state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", 0)
                                btHeadphoneConnected = state == 2
                                LogX.i("HeadphoneProfile: 蓝牙耳机 ${if (btHeadphoneConnected) "连接" else "断开"}")
                                applyHeadphoneProfile(ctx, cfg)
                            }
                        }
                    }
                }
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_HEADSET_PLUG)
                    addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")
                }
                ctx.registerReceiver(receiver, filter)
                LogX.hookSuccess("HeadphoneProfile", "BroadcastReceiver")
            }

            try { LogStore.add("profile", "耳机自动切换: boost=${cfg.headphoneBoostLevel}%") } catch (_: Exception) { }
            try { LogStore.incrementCounter(1) } catch (_: Exception) { }
        } catch (e: Exception) {
            LogX.hookFailed("HeadphoneProfile", "BroadcastReceiver", e)
        }

        hookAudioManagerGetDevices(lpparam, cfg)
    }

    private fun applyHeadphoneProfile(ctx: Context, cfg: AudioConfig) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (headphoneConnected || btHeadphoneConnected) {
            savedBoostLevel = cfg.boostLevel
            savedBassLevel = cfg.bassLevel
            savedEqBands = cfg.eqBands.toList()
            LogX.i("HeadphoneProfile: 应用耳机 Profile boost=${cfg.headphoneBoostLevel}%")
        } else {
            savedBoostLevel?.let { cfg.boostLevel = it }
            savedBassLevel?.let { cfg.bassLevel = it }
            savedEqBands?.let { cfg.eqBands = it.toMutableList() }
            savedBoostLevel = null
            savedBassLevel = null
            savedEqBands = null
            LogX.i("HeadphoneProfile: 恢复扬声器 Profile")
        }
    }

    private fun hookAudioManagerGetDevices(lpparam: XC_LoadPackage.LoadPackageParam, cfg: AudioConfig) {
        try {
            val cls = XposedHelpers.findClassIfExists(
                "android.media.AudioManager", lpparam.classLoader
            ) ?: return
            try {
                XposedHelpers.findAndHookMethod(cls, "getDevices",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(p: MethodHookParam) {
                            val devices = p.result as? Array<AudioDeviceInfo> ?: return
                            for (dev in devices) {
                                val type = dev.type
                                when {
                                    type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                                    type == AudioDeviceInfo.TYPE_WIRED_HEADSET -> {
                                        if (!headphoneConnected) {
                                            headphoneConnected = true
                                            LogX.i("HeadphoneProfile: 检测到有线耳机 (getDevices)")
                                        }
                                    }
                                    type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> {
                                        if (!btHeadphoneConnected) {
                                            btHeadphoneConnected = true
                                            LogX.i("HeadphoneProfile: 检测到蓝牙耳机 (getDevices)")
                                        }
                                    }
                                }
                            }
                        }
                    })
                LogX.hookSuccess("AudioManager", "getDevices")
            } catch (e: Exception) { LogX.w("HeadphoneProfile: getDevices 异常: ${e.message}") }
        } catch (e: Exception) {
            LogX.hookFailed("AudioManager", "getDevices", e)
        }
    }
}
