#!/usr/bin/env python3
# =============================================================================
# ci/scripts/check-pom-parent-relativepath.py
#   —— 守门：每个 pom 的 <parent> 是否能被本地 relativePath 正确解析
#
# 【守什么】
#   Maven 解析 <parent> 的顺序是：**先按 relativePath 找本地文件** ⇒
#   若那份文件的坐标与声明的 parent 坐标不一致 ⇒ Maven 报
#       "Non-resolvable parent POM ... 'parent.relativePath' points at wrong local POM"
#   ⇒ **回落制品仓库**找 com.rag:xxx:pom
#   ⇒ 本仓没有制品仓库 ⇒ **干净环境（CI / 新同事 / CI runner）构建必炸**，
#     而开发机不炸（~/.m2 里躺着旧的已安装 pom，把问题掩盖了）。
#
# 【为什么值得单独设一道门】
#   这是 **2026-10-31 由本 CI 实测抓出**的仓内缺陷（service/users/admin/admin-api）：
#   它的 <parent> 是 com.rag:service，而 Maven 默认 relativePath `../pom.xml`
#   解析到 service/users/admin/pom.xml（坐标 com.rag:admin）⇒ 必炸。
#   ⚠️ 而且**它还会连累别的域**：根聚合 pom 会被 Maven 完整解析，
#      admin-api 解析失败 ⇒ **article / hot / like … 全域的流水线一起红**。
#   ⇒ 一处 pom 缺陷 = 全域 CI 不可用 ⇒ 这类缺陷必须离线拦住，不能等 build 才炸。
#
# 【判定口径（严格照 Maven 的实际行为，不是"看起来对"）】
#   1. <parent> 无 <relativePath>        ⇒ 默认值 `../pom.xml`
#   2. <relativePath></relativePath>     ⇒ 空串 ⇒ Maven **跳过本地查找**
#      ⇒ 直接从仓库解析 ⇒ 合法（根 pom 指向 spring-boot-starter-parent 就是这种）
#      ⇒ **不能**判成缺陷（本门第一版曾把它误判，已按此口径修正）
#   3. relativePath 解析到目录            ⇒ Maven 追加 `pom.xml`
#   4. 解析到的文件不存在                ⇒ Maven 回落仓库 ⇒ **不报错** ⇒ 只告警
#   5. 解析到的文件坐标与声明的 parent 不一致 ⇒ **Maven 直接报致命错** ⇒ 判失败
#
# 【输出】
#   退出码 0 = 全仓通过；1 = 存在致命错（wrong local POM）；2 = 脚本自身异常
# =============================================================================
from __future__ import annotations

import os
import sys
import xml.etree.ElementTree as ET

NS = "{http://maven.apache.org/POM/4.0.0}"
SKIP_DIRS = {".git", "target", ".idea", "node_modules"}


def _text(node, tag):
    if node is None:
        return None
    el = node.find(NS + tag)
    if el is None:
        return None
    val = (el.text or "").strip()
    return val or None


def _find_poms(root):
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        if "pom.xml" in filenames:
            yield os.path.join(dirpath, "pom.xml")


def _parse(path):
    try:
        return ET.parse(path).getroot()
    except Exception as exc:  # noqa: BLE001
        print(f"❌ 无法解析 pom（XML 错误）：{path} ⇒ {exc}", file=sys.stderr)
        return None


def _effective_coords(pom_path, _depth=0):
    """取一个 pom 的**有效** groupId/artifactId/version（自身没写就继承自己的 parent）。"""
    if _depth > 10:
        return None
    root = _parse(pom_path)
    if root is None:
        return None
    parent = root.find(NS + "parent")
    group = _text(root, "groupId")
    version = _text(root, "version")
    if group is None and parent is not None:
        group = _text(parent, "groupId")
    if version is None and parent is not None:
        version = _text(parent, "version")
    artifact = _text(root, "artifactId")
    if group is None or artifact is None:
        # 自身与 parent 都没写 groupId ⇒ 继续往上找（罕见）
        if parent is not None:
            rp = _text(parent, "relativePath")
            base = os.path.dirname(pom_path)
            if rp:
                cand = os.path.normpath(os.path.join(base, rp))
                if os.path.isdir(cand):
                    cand = os.path.join(cand, "pom.xml")
                if os.path.isfile(cand):
                    up = _effective_coords(cand, _depth + 1)
                    if up:
                        return (group or up[0], artifact, version or up[2])
        return None
    return (group, artifact, version)


def _resolve_relative_path(pom_path, parent_node):
    """按 Maven 的规则算出 relativePath 指向的文件；返回 (路径, 是否跳过本地查找)。"""
    rp_node = parent_node.find(NS + "relativePath")
    if rp_node is None:
        rp = "../pom.xml"  # Maven 默认值
    else:
        rp = (rp_node.text or "").strip()
        if rp == "":
            return None, True  # 空串 ⇒ 跳过本地查找（合法）
    cand = os.path.normpath(os.path.join(os.path.dirname(pom_path), rp))
    if os.path.isdir(cand):
        cand = os.path.join(cand, "pom.xml")
    return cand, False


def main():
    root = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else ".")
    poms = sorted(_find_poms(root))
    if not poms:
        print(f"❌ 在 {root} 下没找到任何 pom.xml", file=sys.stderr)
        return 2

    fatal = []   # wrong local POM ⇒ Maven 致命错
    warn = []    # relativePath 指向的文件不存在 ⇒ Maven 回落仓库（不致命）
    checked = 0
    skipped = 0

    for pom in poms:
        root_el = _parse(pom)
        if root_el is None:
            return 2
        parent = root_el.find(NS + "parent")
        if parent is None:
            continue
        checked += 1
        p_group = _text(parent, "groupId")
        p_artifact = _text(parent, "artifactId")
        p_version = _text(parent, "version")

        cand, skip_local = _resolve_relative_path(pom, parent)
        if skip_local:
            skipped += 1
            continue
        rel = os.path.relpath(cand, os.path.dirname(pom))

        if not os.path.isfile(cand):
            warn.append((pom, rel, "relativePath 指向的文件不存在 ⇒ Maven 回落制品仓库解析父 POM"))
            continue

        coords = _effective_coords(cand)
        if coords is None:
            warn.append((pom, rel, "无法解析该 pom 的有效坐标 ⇒ 跳过判定"))
            continue
        c_group, c_artifact, c_version = coords
        mism = []
        if c_group != p_group:
            mism.append(f"groupId 声明={p_group} 实际={c_group}")
        if c_artifact != p_artifact:
            mism.append(f"artifactId 声明={p_artifact} 实际={c_artifact}")
        if p_version is not None and c_version != p_version:
            mism.append(f"version 声明={p_version} 实际={c_version}")
        if mism:
            fatal.append((pom, rel, cand, "；".join(mism)))

    print(f"[pom-parent] 扫描 pom {len(poms)} 个，其中声明了 <parent> 的 {checked} 个")
    print(f"[pom-parent] relativePath 为空串（合法，跳过本地查找）: {skipped} 个")

    for pom, rel, why in warn:
        print(f"⚠️  {pom}  relativePath={rel}  ⇒ {why}")

    for pom, rel, cand, why in fatal:
        print(f"❌ {pom}")
        print(f"     relativePath={rel} ⇒ 解析到 {cand}")
        print(f"     坐标不匹配 ⇒ Maven 报 'parent.relativePath points at wrong local POM'")
        print(f"     {why}")
        print(f"     ⇒ 干净环境构建必炸（回落制品仓库，本仓无仓库）；请把 relativePath 指向真正的那份 pom。")

    if fatal:
        print(f"\n❌ 守门失败：{len(fatal)} 个 pom 的父 POM 无法被本地 relativePath 正确解析")
        return 1
    print(f"\n✅ 守门通过：{checked} 个声明 <parent> 的 pom 全部可被本地 relativePath 正确解析")
    return 0


if __name__ == "__main__":
    sys.exit(main())
