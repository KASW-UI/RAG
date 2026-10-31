#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成「触发面探针」用的**轻量版流水线**。

================================================================================
【为什么需要它】
================================================================================
触发面（哪些 Pipeline 会被某次改动触发）要**在服务端**验证 ⇒ 必须让 Concourse
**真的产生 build**。而：

  · **暂停的 job 不会产生 build** —— 实测 2026-10-31：13 条流水线的 job 全部
    `pause-job` 后，推送 3 个 probe commit ⇒ **一条 build 都没产生**
    ⇒ 调度器对暂停的 job 连资源检测都不做 ⇒ 触发面完全不可观测。
  · **放开 job** ⇒ 每条流水线都会真跑 `mvn package` + `mvn test`（分钟级 × 13 条）
    ⇒ 7 个场景 ≈ 50+ 次构建 ⇒ 算力与磁盘（每个 build 一个 volume）都不可接受
    ⇒ 且**宿主磁盘曾因满盘导致 Docker/Concourse 反复崩溃**。

================================================================================
【轻量版的做法：只换 job 体，paths 块逐字节照抄】
================================================================================
被测的性质是**「git 资源的 `paths` 过滤 + `trigger: true`」** ⇒
与 job 在检出之后做什么**无关**。

⇒ 本脚本：
  1. **逐字节**复制原流水线的 `resources:` 段（**含 `paths:` 列表**）；
  2. 把 `jobs:` 段替换成「同样的 `get: repo` + `trigger: true` + 一个空任务」；
  3. **断言**生成文件里的 `paths:` 段与原文件**逐字节相同**
     ⇒ 若生成逻辑漂了 ⇒ 立刻报错，**不会**拿着一个假的触发面去做验证。

⚠️ **边界声明**：轻量版**只能**验证「哪些流水线会被触发」，
   **不能**验证流水线内部各 step 的执行结果（那部分由真实 build 覆盖）。
"""
import os
import re
import sys


def extract_paths_block(text: str) -> str:
    """取出 `resources:` 段里 `paths:` 那一块的**原文**（逐字节）。"""
    m = re.search(r"\n([ \t]*)paths:\n((?:[ \t]*-[ \t]*\S+[ \t]*\n)+)", text)
    if not m:
        raise SystemExit("❌ 该流水线没有 paths 块 ⇒ 无法做触发面探针")
    return m.group(0)


def extract_job_name(text: str) -> str:
    m = re.search(r"\njobs:\n- name: (\S+)\n", text)
    if not m:
        raise SystemExit("❌ 找不到 job 名")
    return m.group(1)


def extract_resources_section(text: str) -> str:
    """取出 `resources:` 段原文（到 `jobs:` 之前）。"""
    i = text.index("\nresources:")
    j = text.index("\njobs:")
    sec = text[i:j]
    # 🆕 把资源检查间隔调小（默认 60s ⇒ 13 个资源实例的检查时刻散开 ⇒
    #    「等第一个 build」会得到**残缺**的触发集合 ⇒ 测量竞态）。
    #    ⚠️ 这只改**时机**，不改**哪些流水线会触发** ⇒ 被测性质不变。
    if "check_every:" not in sec:
        sec = sec.replace("    branch: ((git_branch))\n",
                          "    branch: ((git_branch))\n"
                          "    check_every: 15s\n")
    return sec


LITE_JOB = """
jobs:
- name: {job}
  plan:
  - get: repo
    trigger: true
  # 🆕 空任务：只为了让 build 跑起来并被观测到 ⇒ 零算力、零磁盘
  - task: trigger-probe
    config:
      platform: linux
      image_resource:
        type: registry-image
        source:
          repository: ((registry))/library/python
          tag: 3.12-bookworm
      inputs:
      - name: repo
      run:
        path: sh
        args:
        - -c
        - echo trigger-probe
"""


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print("用法: make-trigger-probe-pipelines.py <流水线目录> <输出目录>",
              file=sys.stderr)
        return 64
    src_dir, out_dir = argv[1], argv[2]
    os.makedirs(out_dir, exist_ok=True)

    made = []
    for name in sorted(os.listdir(src_dir)):
        if not (name.startswith("rag-") and name.endswith(".yml")):
            continue
        src = os.path.join(src_dir, name)
        with open(src, encoding="utf-8") as f:
            text = f.read()
        res = extract_resources_section(text)
        paths_before = extract_paths_block(text)
        job = extract_job_name(text)
        lite = res + LITE_JOB.format(job=job)
        # ⚠️ **逐字节断言**：paths 块必须与原文件完全一致
        paths_after = extract_paths_block(lite)
        if paths_after != paths_before:
            raise SystemExit(
                f"❌ {name}：轻量版的 paths 块与原文件**不一致** ⇒ "
                f"拿它做触发面验证就是造假\n--- 原 ---\n{paths_before}\n--- 新 ---\n{paths_after}")
        out = os.path.join(out_dir, name)
        with open(out, "w", encoding="utf-8") as f:
            f.write(lite)
        made.append((name, job, len(paths_before.strip().splitlines())))

    if not made:
        raise SystemExit("❌ 没有找到任何 rag-*.yml")
    print(f"✅ 生成 {len(made)} 条轻量版流水线（paths 块已逐字节校验）→ {out_dir}")
    for name, job, n in made:
        print(f"   · {name:<28} job={job:<18} paths {n} 条")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
