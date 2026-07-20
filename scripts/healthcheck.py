#!/usr/bin/env python3
"""
LSP-Model 模块体检脚本
输出 JSON 报告到 stdout，供 Web 工具渲染。
体检维度见 AI_DEV_GUIDE.md §6.1
"""
import os, re, glob, json, sys
from datetime import datetime, timezone

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODULES_DIR = os.path.join(ROOT, "modules")
VERSION = "1.0.10"

def scan_module(mod_dir, mod_name):
    """扫描单个模块"""
    result = {
        "name": mod_name,
        "version": VERSION,
        "hooks": [],
        "uiBugs": 0,
        "configIssues": [],
        "stats": {"hookFiles": 0, "ok": 0, "weak": 0, "hollow": 0, "totalHookCalls": 0}
    }

    # 1. Hook 体检
    hook_files = glob.glob(f"{mod_dir}/app/src/main/java/**/hooks/*.kt", recursive=True)
    result["stats"]["hookFiles"] = len(hook_files)

    for f in sorted(hook_files):
        with open(f, errors='replace') as fh:
            c = fh.read()
        name = os.path.basename(f)
        hook_calls = len(re.findall(r'findAndHookMethod|findAndHookConstructor|hookAllMethods|XposedBridge\.hook(?:Method|AllMethods)?\(', c))
        try_catch = len(re.findall(r'\bcatch\b', c))
        logs = len(re.findall(r'LogX\.|Log\.|XposedBridge\.log', c))
        todo = len(re.findall(r'TODO|FIXME|未实现|待实现|placeholder|stub', c, re.I))
        lines = c.count('\n') + 1

        # 判定状态
        is_utility = bool(re.search(r'工具类|工具|提供.*查询|供.*调用', c[:500]))
        if hook_calls == 0 and is_utility:
            status = "utility"  # 工具类，合理
        elif hook_calls == 0:
            status = "hollow"
            result["stats"]["hollow"] += 1
        elif hook_calls < 2 and try_catch > hook_calls * 2:
            status = "weak"
            result["stats"]["weak"] += 1
        else:
            status = "ok"
            result["stats"]["ok"] += 1

        result["stats"]["totalHookCalls"] += hook_calls
        result["hooks"].append({
            "file": name,
            "hookCalls": hook_calls,
            "tryCatch": try_catch,
            "logs": logs,
            "todo": todo,
            "lines": lines,
            "status": status
        })

    # 2. UI bug 体检（cfg.X = it 直接改字段，未用 cfg.copy）
    ui_files = glob.glob(f"{mod_dir}/app/src/main/java/**/ui/**/*.kt", recursive=True)
    ui_bugs = 0
    for f in ui_files:
        with open(f, encoding='utf-8', errors='replace') as fh:
            c = fh.read()
        # 用 finditer 精确匹配 cfg.X = it 且附近无 copy(
        for m in re.finditer(r'cfg\.\w+\s*=\s*it(?!\.\w)', c):
            start = max(0, m.start() - 20)
            end = min(len(c), m.end() + 20)
            if 'copy(' not in c[start:end]:
                ui_bugs += 1
    result["uiBugs"] = ui_bugs

    # 3. 配置一致性
    gradle = f"{mod_dir}/app/build.gradle.kts"
    if os.path.isfile(gradle):
        with open(gradle) as fh:
            gc = fh.read()
        checks = {
            "versionName匹配VERSION": f'versionName = "{VERSION}"' in gc,
            "versionCode=1": 'versionCode = 1' in gc,
            "signingConfig": 'meng411722' in gc,
            "compose=true": 'compose = true' in gc,
            "jvmTarget=17": 'jvmTarget = "17"' in gc,
            "noBOM": 'compose-bom' not in gc,
            "xposedCompileOnly": 'compileOnly("de.robv.android.xposed:api:82")' in gc,
        }
        for k, v in checks.items():
            if not v:
                result["configIssues"].append(k)

    # 4. Manifest 检查
    manifest = f"{mod_dir}/app/src/main/AndroidManifest.xml"
    if os.path.isfile(manifest):
        with open(manifest) as fh:
            mc = fh.read()
        for meta in ['xposedmodule', 'xposedminversion', 'xposeddescription', 'xposedscope']:
            if f'android:name="{meta}"' not in mc:
                result["configIssues"].append(f"manifest缺{meta}")

    # 5. xposed_init 检查
    xposed_init = f"{mod_dir}/app/src/main/assets/xposed_init"
    if os.path.isfile(xposed_init):
        with open(xposed_init) as fh:
            init = fh.read().strip()
        if not init.endswith("XposedLoader"):
            result["configIssues"].append(f"xposed_init非XposedLoader: {init}")

    # 6. LSPatch 合规检查（仅 NoRoot 版 + MicroXEnhancer）
    is_noroot = mod_name.endswith("_NoRoot") or mod_name == "MicroXEnhancer"
    if is_noroot:
        import re as _re
        # xposedminversion 应为 93
        mv = _re.search(r'xposedminversion.*?android:value="(\d+)"', mc)
        if mv and mv.group(1) != "93":
            result["configIssues"].append(f"LSPatch: xposedminversion={mv.group(1)}(应93)")
        # FOREGROUND_SERVICE 权限
        if 'android.permission.FOREGROUND_SERVICE' not in mc:
            result["configIssues"].append("LSPatch: 缺FOREGROUND_SERVICE权限")
        # XposedLoader 包名过滤 + 进程过滤
        xl_files = __import__('glob').glob(f"{mod_dir}/app/src/main/java/**/XposedLoader.kt", recursive=True)
        for xlf in xl_files:
            with open(xlf) as fh: xc = fh.read()
            if 'packageName == "android"' not in xc and '"android" == lpparam.packageName' not in xc:
                result["configIssues"].append("LSPatch: 缺android包名过滤")
            if 'isFirstApplication' not in xc:
                result["configIssues"].append("LSPatch: 缺isFirstApplication进程过滤")

    return result

def main():
    report = {
        "version": VERSION,
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "modules": []
    }
    for mod in sorted(os.listdir(MODULES_DIR)):
        mod_dir = os.path.join(MODULES_DIR, mod)
        if not os.path.isdir(mod_dir) or mod == "keystore":
            continue
        report["modules"].append(scan_module(mod_dir, mod))

    # 汇总（工具类不计入健康分分母）
    total = sum(m["stats"]["hookFiles"] for m in report["modules"])
    ok = sum(m["stats"]["ok"] for m in report["modules"])
    weak = sum(m["stats"]["weak"] for m in report["modules"])
    hollow = sum(m["stats"]["hollow"] for m in report["modules"])
    utility = sum(1 for m in report["modules"] for h in m["hooks"] if h["status"] == "utility")
    ui_bugs = sum(m["uiBugs"] for m in report["modules"])
    scored = ok + weak + hollow  # 参与评分的文件数（排除工具类）
    report["summary"] = {
        "totalModules": len(report["modules"]),
        "totalHookFiles": total,
        "ok": ok, "weak": weak, "hollow": hollow, "utility": utility,
        "uiBugs": ui_bugs,
        "healthScore": round(ok * 100 / scored, 1) if scored else 0
    }

    print(json.dumps(report, ensure_ascii=False, indent=2))

if __name__ == "__main__":
    main()
