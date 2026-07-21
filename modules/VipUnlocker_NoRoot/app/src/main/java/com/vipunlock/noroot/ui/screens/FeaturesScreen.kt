package com.vipunlock.noroot.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vipunlock.noroot.models.VipConfig
import com.vipunlock.noroot.ui.components.FeatureCard
import com.vipunlock.noroot.utils.ConfigManager

@Composable
fun FeaturesScreen(cfg: VipConfig, onConfigChange: (VipConfig) -> Unit) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        // ===== 音乐�?=====
        Text("音乐�?VIP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "网易云音�?黑胶VIP", "Hook VIP 状态查询方法，返回已订阅黑胶VIP",
            cfg.netEaseVipEnabled,
            { val nc = cfg.copy(netEaseVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "QQ音乐 豪华绿钻", "Hook 绿钻状态查询，返回已开通豪华绿�?,
            cfg.qqMusicVipEnabled,
            { val nc = cfg.copy(qqMusicVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "酷狗音乐 豪华VIP", "通用候选类�?Hook，返回已订阅",
            cfg.kugouVipEnabled,
            { val nc = cfg.copy(kugouVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "酷我音乐 SVIP", "通用候选类�?Hook，返回已订阅",
            cfg.kuwoVipEnabled,
            { val nc = cfg.copy(kuwoVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))

        // ===== 视频�?=====
        Text("视频�?VIP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "爱奇�?黄金会员", "Hook VIP 状态查询方法，返回黄金会员",
            cfg.iqiyiVipEnabled,
            { val nc = cfg.copy(iqiyiVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "优酷 VIP会员", "通用候选类�?Hook，返�?VIP 已开�?,
            cfg.youkuVipEnabled,
            { val nc = cfg.copy(youkuVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "腾讯视频 SVIP", "通用候选类�?Hook，返�?SVIP",
            cfg.tencentVideoVipEnabled,
            { val nc = cfg.copy(tencentVideoVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "哔哩哔哩 大会�?, "Hook 大会员状态查询方法，返回年度大会�?,
            cfg.biliVipEnabled,
            { val nc = cfg.copy(biliVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))

        // ===== 阅读/资讯�?=====
        Text("阅读/资讯�?VIP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "喜马拉雅 VIP", "通用候选类�?Hook，返回已订阅 VIP",
            cfg.ximalayaVipEnabled,
            { val nc = cfg.copy(ximalayaVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "今日头条 关键功能", "通用候选类�?Hook，解锁部分付费功�?,
            cfg.toutiaoVipEnabled,
            { val nc = cfg.copy(toutiaoVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "知乎 盐选会�?, "通用候选类�?Hook，返回盐选会员已开�?,
            cfg.zhihuVipEnabled,
            { val nc = cfg.copy(zhihuVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))

        // ===== 工具�?=====
        Text("工具�?VIP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "百度网盘 SVIP", "通用候选类�?Hook，返�?SVIP",
            cfg.baiduNetdiskVipEnabled,
            { val nc = cfg.copy(baiduNetdiskVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "WPS 超级会员", "通用候选类�?Hook，返回超级会员已开�?,
            cfg.wpsVipEnabled,
            { val nc = cfg.copy(wpsVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "微信读书 无限�?, "通用候选类�?Hook，返回无限卡已订�?,
            cfg.wereadVipEnabled,
            { val nc = cfg.copy(wereadVipEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) }
        )

        Spacer(Modifier.height(20.dp))

        // ===== Shizuku 增强 =====
        Text("Shizuku 增强（adb-level�?, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "Shizuku VIP 数据库增�?,
            "通过 Shizuku sqlite3/content/pm grant 直接修改本地 VIP 数据库实现持久化解锁（需 Shizuku 运行中）",
            cfg.shizukuVipDbEnabled,
            { val nc = cfg.copy(shizukuVipDbEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )

        Spacer(Modifier.height(20.dp))

        // ===== 实验�?=====
        Text("实验性功�?, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "通用 VIP 尝试", "Hook isVip/isPremium/getVipLevel 等通用方法名，跨APP通用",
            cfg.universalVipTryEnabled,
            { val nc = cfg.copy(universalVipTryEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "通用去广�?, "Hook 穿山�?GDT/百度/快手/Mintegral 等广�?SDK",
            cfg.removeAdsEnabled,
            { val nc = cfg.copy(removeAdsEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
        Spacer(Modifier.height(8.dp))

        FeatureCard(
            "绕过签名/完整性校�?, "Hook APP 自实现校验方�?+ 拦截 su/Root 检测命�?,
            cfg.bypassVerifyEnabled,
            { val nc = cfg.copy(bypassVerifyEnabled = it); ConfigManager.saveGlobalConfig(nc); onConfigChange(nc) },
            experimental = true
        )
    }
}
