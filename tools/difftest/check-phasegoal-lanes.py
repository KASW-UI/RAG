#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 / 校验 **每 lane 的阶段一验收矩阵**（`docs/PhaseGoal/g1.md` … `g4.md`）。

**为什么要有这个**（AGENTS.md §5.3d/§5.3e/§5.3g）：
- G1~G4 是**并发执行**的 4 棵工作树，各自有写权边界；
- 但**阶段一的验收基线**（`docs/PhaseGoal-judge.md`）是**按域/组件**组织的，**没有 lane 维度**
  ⇒ 「我这棵 lane 的阶段一验收矩阵长什么样」在基线里**查不到**；
- 手抄一份 per-lane 矩阵 = 再造一个**会漂、且必然与基线打架**的副本。

⇒ 本工具**从基线推导**出 per-lane 矩阵并落盘：
  · **§1 组件行** = 基线 A-2 按 lane 归属过滤 ⇒ **不手抄**；
  · **§2 自修 / 转派 G4** = A-2「仍开放项」列按落点路由（`service/common` / `users-common` ⇒ G4）；
  · **§3 P0 分摊 / §4 矩阵 C 分摊** = 本文件里**写死**的归属表（每条 P0/X **有且仅有一个主责 lane**），
    并由本工具**校验覆盖无重无漏**；
  · **§5 收口命令** = 本文件里写死的 per-lane 命令清单（**路径必须在仓内存在**）。
  · **§6 判定** = MIN(本 lane 组件状态)，序 ✅ > 🟡 > ⚫ > 🔴（口径唯一出处 = 基线 §8.1）。

用法：
    python3 tools/difftest/check-phasegoal-lanes.py --write   # 生成/更新 4 份文件
    python3 tools/difftest/check-phasegoal-lanes.py           # 只校验（CI 用；不写文件）
    python3 tools/difftest/check-phasegoal-lanes.py --verbose # 逐条打印

退出码：0 = 通过；1 = 有检查失败。
"""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASE = ROOT / "docs/PhaseGoal-judge.md"
OUT_DIR = ROOT / "docs/PhaseGoal"
LANES = ("G1", "G2", "G3", "G4")

# ── lane 归属（AGENTS.md §5.3d：G1 = users 不含 users-common + follow；
#              §5.3f：users-common 与 service/common 都是共享层 ⇒ 写权归 G4）──
DOM_LANE = {
    "article": "G3", "comment": "G3", "favorite": "G3", "hot": "G3",
    "like": "G2", "message": "G2", "task": "G2",
    "users": "G1", "follow": "G1",
}
SHARED_COMPONENTS = {"service/common", "users-common"}  # ⇒ G4
LANE_TITLE = {
    "G1": ("align/g1-users-follow", "`users` 域（**不含 `users-common`**）+ `follow`"),
    "G2": ("align/g2-message-like-task", "`message` + `like` + `task`"),
    "G3": ("align/g3-article-comment-favorite-hot", "`article` + `comment` + `favorite` + `hot`"),
    "G4": ("align/g4-service-common", "**共享层** `service/common` + `users-common`"),
}
LANE_DOMAINS = {
    "G1": ["users", "follow"],
    "G2": ["message", "like", "task"],
    "G3": ["article", "comment", "favorite", "hot"],
    "G4": [],
}
STATUS_RANK = {"✅": 4, "🟡": 3, "⚫": 2, "🔴": 1}

# ── P0 九条归属（主责唯一；collab = 协作方）──
# owner ∈ {G1..G4, 收口}
P0_MAP = {
    1: ("G2", ["G1", "G3", "G4"],
        "核心业务链路**真实运行**",
        "`like` 域事实投递链路（`DRIFT-LIKE-003`）仍 `UNRESOLVED`；`like` / `follow` / `users-common` "
        "的**双侧活体探针不在版本控制内**（G-1/G-2/G-4）⇒ 该链路「真实运行 + 行为等价」**未取证**；"
        "`article` 域双侧活体 **0 条**（G-3，Go 侧需 `etcd:2379` 宿主端口映射）",
        "`bash tools/semantic-probe/run-parity.sh`（message）· `bash tools/difftest/comment/run_diff.sh`"
        "（comment）· `bash tools/difftest/run-task-http-parity.sh`（task）· `bash tools/difftest/run-hot.sh`（hot）"
        " · `bash tools/difftest/run.sh`（users）· `bash tools/api-envelope-probe/favorite/run.sh`（favorite）"
        " · `python3 tools/difftest/check-surface-parity.py`（契约面）"),
    2: ("G1", ["G2", "G3"],
        "参数校验 / 业务规则 / 认证 / 权限**四项**有效",
        "「每端点 ≥1 条失败路径测试」**只能证明存在失败路径处理**，推不出四项都有效；"
        "且失败路径矩阵**域覆盖不全**（`article`/`follow`/`task`/`hot` **无独立失败路径矩阵**）",
        "`bash tools/difftest/matrix.py`（users 字段级笛卡尔）· `bash tools/semantic-probe/run-parity.sh`"
        "（message 23 例）· `bash tools/json-body-probe/run-parity.sh`（hot 20 例）"
        " · `bash tools/difftest/comment/run_diff.sh`（comment 23 步）· 各域台账条目级状态"),
    3: ("G1", ["G2", "G3"],
        "持久化 / 事务边界 / 无数据丢失重复状态错误",
        "`check-ddl-static.py` **默认不开 `--indexes`** ⇒ **索引 / 唯一约束 / 触发器全部未查**"
        "⇒ 基线 §3.1 对照清单的最后两项至今**没有机器核过**；且它比的是**活库**不是仓库 `.sql`",
        "`python3 tools/difftest/check-ddl-static.py --domain <域> --indexes`"
        " · `bash tools/difftest/run-message-ddl-parity.sh`"),
    4: ("G3", ["G1", "G2"],
        "核心失败路径有明确行为，不无声吞异常 / 返回误导性成功",
        "失败路径探针**只覆盖 5 个域**（article / message / hot / shared-layer / favorite）；"
        "**`article` 域（失败路径最密集：下载/上传/审核/`S-5`）没有失败路径探针**",
        "`bash tools/semantic-probe/run-parity.sh`（含 known-dev 白名单）"
        " · `bash tools/json-body-probe/run-parity.sh`（**无白名单**，任何不一致即真漂移）"),
    5: ("收口", [], "按实际项目说明完成构建 / 配置 / 启动",
        "README **21 字节**、无 `.env.example`、`docs/FileTree/Infrastructure.md` 描述的是**旧架构**",
        "人工 + `docker compose config`"),
    6: ("G1", ["G2", "G3"], "核心业务有可执行自动化测试，且断言真实业务结果",
        "全量回归 **2860 条 / 0F / 0E / 4S**（52 模块，42:41 min）；⚠️ **必须带 `clean`**"
        "（否则 stale 产物同时伪造「红」与「条数」）",
        "`mvn -o -B -DargLine=-Xmx2g clean test` + surefire `TEST-*.xml` 逐 `testsuite` 求和"),
    7: ("收口", [], "模块职责清晰，能追踪核心调用链",
        "基线 A-1 的「Go 原件 ↔ RAG 实现」两列已给；各域各有一份 `vs-common.md`（A1~A9 机制面轴）",
        "`docs/DRIFT/_cross/component-coverage-matrix.md`"),
    8: ("G1", ["G2", "G3", "收口"], "关键配置与敏感信息无明显不安全处理",
        "**E4 实测**：`docker compose config` ⇒ 渲染 `USER_JWT_SECRET: \"\"`；"
        "Spring `PropertyPlaceholderHelper` 实测「属性存在但为空串 ⇒ 解析为空串，**不走默认值**」"
        "⇒ **不设变量起栈 ⇒ 空签名密钥 ⇒ token 可伪造**；10 处 yaml 带明文默认值（含 `like-api:62` 裸字面量）；"
        "根级 `docker-compose.yml` `:146`/`:193` 缺 `:?`（**根级属收口**）",
        "`docker compose config` + 空变量起栈实测"),
    9: ("收口", [], "已知严重缺陷 / 无法验证的关键能力 / 运行前提有明确记录",
        "38 份组件台账 + `docs/Intentional_Deviations/`（**48 条已签字 + 18 条未签字候选**）"
        "+ 基线 §九 / §十一；⚠️ 共享索引对 `favorite` 域仍写「零台账」已被实测推翻（G-11，**SHARED 面**）",
        "`python3 tools/check-ledger-tables.py` ⇒ EXIT=0"),
}

# ── 矩阵 C 12 条归属 ──
X_MAP = {
    1: ("G1", ["G2", "G3"], "共享 Flyway 账本隔离",
        "7 个 rpc 模块各自独立账本表；**同库三进程并发**不撞号；"
        "⚠️ `favorite-rpc` 缺 `baseline-version: 0` ⇒ 只记账",
        "`python3 tools/difftest/check-flyway-ledger.py`（**仅静态 yaml**）"
        " · `bash tools/difftest/check-flyway-concurrent.sh`（**真并发**，本轮未跑）"),
    2: ("G1", ["G2", "G3"], "DDL 双侧活体逐列",
        "类型/长度 **9 域 0 差异**；可空性 / DEFAULT / 索引 / 唯一约束 / 触发器逐项一致或**已签字保留**；"
        "⚠️ **默认不开 `--indexes` ⇒ 索引/约束/触发器全部未查**；`hot` = **N/A** ⇒ 不许写成「已对齐」",
        "`python3 tools/difftest/check-ddl-static.py --domain <域> --indexes`"),
    3: ("G1", ["G2", "G3"], "gRPC 拦截器真接线",
        "每个 rpc 模块的拦截器**真挂上 server**（**纯静态 grep，不启动服务**）",
        "`python3 tools/difftest/check-grpc-interceptor-wiring.py` ⇒ EXIT=0"),
    4: ("G1", ["G2", "G3"], "api 容器上限三旋钮",
        "三个旋钮的**配置值**与 Go 目标值一致（**只读 `application.yml`，不验证运行时**）；"
        "运行时由 `HotApiRealTomcatGateTest` 3 条真 Tomcat 断言覆盖",
        "`python3 tools/difftest/check-tomcat-limits.py` ⇒ EXIT=0"),
    5: ("G1", ["G2", "G3"], "产物可执行性",
        "每个 fat jar **可执行**（含 `Main-Class`）；⚠️ 本轮实跑**只覆盖 5/20 模块**"
        "（15 个「未构建」被 `continue` 跳过且**不计失败**）⇒ 需先 `mvn package` 再跑",
        "`bash tools/difftest/check-jar-executable.sh` ⇒ EXIT=0"),
    6: ("G1", ["G2", "G3", "G4"], "deadline 分层预算与传播",
        "下游 deadline = `min(客户端 deadline, 服务端 timeout)`；SQL 取消时刻 == deadline 时刻",
        "`python3 tools/difftest/check-deadline-verdict.py`（**门禁自身有效性**）"
        " · `python3 tools/difftest/check-deadline-downstream.py`（**真双侧活体**）"
        " · `bash tools/breaker-probe/run-parity.sh`（共享层）"),
    7: ("G1", ["G2", "G3", "G4"], "错误码契约（跨域）",
        "码值 + 文案 + 未知码回落**逐字节一致**；业务码经 gRPC 可还原；"
        "⚠️ **只覆盖 2/9 域**的双侧逐字节对拍（article + favorite），其余 7 域只有**域内契约测试**",
        "`bash tools/errmsg-probe/run-parity.sh`（article）· `bash tools/errmsg-probe/favorite/run-parity.sh`"
        " · 各域 `*ErrorCodeContractTest`"),
    8: ("G1", ["G2", "G3"], "HTTP 路径 / 方法契约",
        "(method, path) 归一化后**集合相等**；⚠️ **JWT 保护面未自动比对**"
        "（RAG 侧写在拦截器内部名单，非 `addPathPatterns`）；`users`/`admin` = **已签字偏离**",
        "`python3 tools/difftest/check-surface-parity.py --domain <域>` ⇒ **HTTP 端点 Go 72 ≡ RAG 72**"),
    9: ("G1", ["G2", "G3"], "gRPC 方法覆盖",
        "Go proto `rpc` 集合 ≡ RAG `*ImplBase` 覆写集合（大小写归一）",
        "`python3 tools/difftest/check-surface-parity.py` ⇒ **gRPC 方法 Go 71 ≡ RAG 71**"),
    10: ("G1", ["G2", "G3"], "proto 契约 + 消费方式",
         "行为层零差异 + `optional` presence + 无字段号硬编码 + 消费点一致 + wire 字节；"
         "⚠️ **落仓静态守门只覆盖 4/10**（user/admin/task/comment）⇒ `follow`/`like`/`hot` **连静态守门都没有**；"
         "**消费方式**在 10 个 proto 上全部未取证（G-19）",
         "`bash tools/difftest/check-proto-static.sh` · `python3 tools/difftest/comment/check-proto-parity.py`"
         " · `bash tools/proto-wire-probe/favorite/run.sh`"),
    11: ("G1", ["G2", "G3", "收口"], "敏感配置链",
         "密钥**无明文默认值**；缺变量 ⇒ **启动失败**；🔴 10 处 yaml 带明文默认值"
         "（含 `like-api:62` 裸字面量）+ 根级 compose `:146`/`:193` 缺 `:?` ⇒ **空签名密钥可起栈**（E4 实测）",
         "`docker compose config` + 空变量起栈实测"),
    12: ("收口", [], "构建 / 配置 / 启动说明",
         "README ≥ 1 屏且能照着构建启动；`.env.example` 存在；compose fail-closed；"
         "🔴 README **21 字节**、无 `.env.example`、`docs/FileTree/Infrastructure.md` 描述**旧架构**",
         "人工 + `docker compose config`"),
}

# ── per-lane 收口验收命令（路径必须真实存在）──
LANE_COMMANDS = {
    "G1": [
        "# ── 契约面 / DDL / proto ─────────────────────────────────────",
        "python3 tools/difftest/check-surface-parity.py --domain users",
        "python3 tools/difftest/check-surface-parity.py --domain follow",
        "python3 tools/difftest/check-ddl-static.py --domain users --indexes",
        "python3 tools/difftest/check-ddl-static.py --domain follow --indexes",
        "bash tools/difftest/check-proto-static.sh",
        "# ── 双侧活体对拍 ────────────────────────────────────────────",
        "bash tools/difftest/run.sh                    # users 域 HTTP wire（diff.py）",
        "python3 tools/difftest/matrix.py              # users 域 DTO 字段边界（字段列表取自真源 types.go）",
        "bash tools/difftest/exp-difftest-grpc-user9.sh      # users 域 gRPC 43 场景",
        "bash tools/difftest/exp-difftest-grpc-admin-errors.sh --stacks-up   # admin 域 18 cases",
        "bash tools/difftest/exp-difftest-redis-blacklist.sh  # 黑名单链路",
        "# ── 产物 / 装配 ─────────────────────────────────────────────",
        "bash tools/difftest/check-jar-executable.sh    # ⚠️ 须先 mvn package，否则只覆盖 5/20",
        "python3 tools/difftest/check-grpc-interceptor-wiring.py",
        "python3 tools/difftest/check-tomcat-limits.py",
        "# ── 本 lane 模块级测试（必须带 -am）─────────────────────────",
        "mvn -o -B -DargLine=-Xmx2g -DfailIfNoTests=false --fail-at-end -pl service/users/user/user-api,service/users/user/user-rpc,service/users/admin/admin-api,service/users/admin/admin-rpc,service/users/users-common,service/follow/follow-api,service/follow/follow-rpc,service/follow/follow-common -am test",
    ],
    "G2": [
        "# ── 契约面 / DDL / proto ─────────────────────────────────────",
        "python3 tools/difftest/check-surface-parity.py --domain message",
        "python3 tools/difftest/check-surface-parity.py --domain like",
        "python3 tools/difftest/check-surface-parity.py --domain task",
        "python3 tools/difftest/check-ddl-static.py --domain message --indexes",
        "python3 tools/difftest/check-ddl-static.py --domain like --indexes",
        "python3 tools/difftest/check-ddl-static.py --domain task --indexes",
        "bash tools/difftest/check-proto-static.sh",
        "# ── 双侧活体对拍 ────────────────────────────────────────────",
        "bash tools/semantic-probe/run-parity.sh                  # message 解析语义 23 例",
        "bash tools/difftest/exp-difftest-grpc-errors-message.sh  # message gRPC 活体往返",
        "bash tools/difftest/run-message-ddl-parity.sh            # message DDL 双侧",
        "bash tools/difftest/run-task-http-parity.sh              # task HTTP 44 步",
        "bash tools/difftest/run-task-grpc-xlang.sh              # task 跨语言 gRPC",
        "bash tools/difftest/run-task-kafka-e2e.sh                # task Kafka 端到端",
        "bash tools/difftest/run-task-offset-oldest.sh            # task offset 语义",
        "# ── 产物 / 装配 ─────────────────────────────────────────────",
        "bash tools/difftest/check-jar-executable.sh    # ⚠️ 须先 mvn package，否则只覆盖 5/20",
        "python3 tools/difftest/check-grpc-interceptor-wiring.py",
        "python3 tools/difftest/check-tomcat-limits.py",
        "# ── 本 lane 模块级测试（必须带 -am）─────────────────────────",
        "mvn -o -B -DargLine=-Xmx2g -DfailIfNoTests=false --fail-at-end -pl service/message/message-api,service/message/message-rpc,service/message/message-common,service/like/like-api,service/like/like-rpc,service/like/like-common,service/task/task-api,service/task/task-rpc -am test",
    ],
    "G3": [
        "# ── 契约面 / DDL / proto ─────────────────────────────────────",
        "python3 tools/difftest/check-surface-parity.py --domain article",
        "python3 tools/difftest/check-surface-parity.py --domain comment",
        "python3 tools/difftest/check-surface-parity.py --domain favorite",
        "python3 tools/difftest/check-surface-parity.py --domain hot",
        "python3 tools/difftest/check-ddl-static.py --domain article --indexes",
        "python3 tools/difftest/check-ddl-static.py --domain comment --indexes",
        "python3 tools/difftest/check-ddl-static.py --domain favorite --indexes",
        "# ⚠️ hot 域 = N/A（Go 侧无对应 DDL）⇒ 不许写成「已对齐」",
        "bash tools/difftest/check-proto-static.sh",
        "# ── 双侧活体对拍 ────────────────────────────────────────────",
        "bash tools/difftest/comment/run_diff.sh                # comment 23 步矩阵",
        "bash tools/difftest/run-hot.sh                        # hot 域双侧 29 例",
        "bash tools/difftest/run-hot-rpc-difftest.sh           # hot-rpc gRPC 逐字节（真 Kafka + 2×真 Redis）",
        "bash tools/api-envelope-probe/favorite/run.sh        # favorite 响应字节",
        "bash tools/api-full-parity-probe/favorite/run.sh     # favorite 全 HTTP 面 32 例",
        "bash tools/proto-wire-probe/favorite/run.sh          # favorite wire 字节",
        "# ── 产物 / 装配 ─────────────────────────────────────────────",
        "bash tools/difftest/check-jar-executable.sh    # ⚠️ 须先 mvn package，否则只覆盖 5/20",
        "python3 tools/difftest/check-grpc-interceptor-wiring.py",
        "python3 tools/difftest/check-tomcat-limits.py",
        "# ── 本 lane 模块级测试（必须带 -am）─────────────────────────",
        "mvn -o -B -DargLine=-Xmx2g -DfailIfNoTests=false --fail-at-end -pl service/article/article-api,service/article/article-rpc,service/article/article-common,service/comment/comment-api,service/comment/comment-rpc,service/comment/comment-common,service/favorite/favorite-api,service/favorite/favorite-rpc,service/favorite/favorite-common,service/hot/hot-api,service/hot/hot-rpc,service/hot/hot-common -am test",
    ],
    "G4": [
        "# ── 共享层语义探针 ──────────────────────────────────────────",
        "bash tools/breaker-probe/run-parity.sh                            # 共享层熔断逐条",
        "bash tools/semantic-probe/deadline-downstream/run-deadline-downstream-parity.sh",
        "python3 tools/difftest/check-deadline-verdict.py                  # 门禁自身有效性（变异）",
        "python3 tools/difftest/check-deadline-downstream.py",
        "# ── 账本 / 装配 ──────────────────────────────────────────────",
        "python3 tools/difftest/check-flyway-ledger.py",
        "bash tools/difftest/check-flyway-concurrent.sh   # ⚠️ 真 PG 三进程并发，本轮未跑",
        "python3 tools/difftest/check-grpc-interceptor-wiring.py",
        "python3 tools/difftest/check-grpc-runtime-classpath.py",
        "# ── 共享层模块级测试（必须带 -am）───────────────────────────",
        "mvn -o -B -DargLine=-Xmx2g -DfailIfNoTests=false --fail-at-end -pl service/common,service/users/users-common -am test",
    ],
}

FAILURES: list[str] = []
NOTES: list[str] = []


def fail(check: str, msg: str) -> None:
    FAILURES.append(f"[{check}] {msg}")


def note(msg: str) -> None:
    NOTES.append(msg)


# ── 基线解析 ────────────────────────────────────────────────────────────
def split_row(line: str) -> list[str]:
    s = line.strip()
    if s.startswith("|"):
        s = s[1:]
    if s.endswith("|"):
        s = s[:-1]
    return [p.strip() for p in re.split(r"(?<!\\)\|", s)]


def read_baseline_rows():
    """返回 A-2 的 39 行（域, 组件, R, E, 状态, 必达终点指标, 判定命令, 仍开放项, 权威来源）。"""
    rows = []
    lines = BASE.read_text(encoding="utf-8").split("\n")
    in_a2 = False
    for l in lines:
        if l.startswith("## 三、矩阵 A-2"):
            in_a2 = True
            continue
        if in_a2 and l.startswith("## "):
            break
        if not in_a2 or not l.startswith("|"):
            continue
        c = split_row(l)
        if len(c) < 9 or set("".join(c)) <= set("-|: "):
            continue
        if c[0].startswith("域"):  # 表头
            continue
        dom = re.sub(r"[`*]", "", c[0])
        comp = re.sub(r"[`*]", "", c[1])
        if dom not in DOM_LANE and comp not in SHARED_COMPONENTS:
            continue
        rows.append(dict(dom=dom, comp=comp, r=re.sub(r"[`*↑↓ ]", "", c[4]),
                         e=re.sub(r"[`* ]", "", c[5]), st=c[8].strip(),
                         metric=c[2], cmd=c[3], open_=c[7]))
    return rows


def lane_of(row) -> str:
    if row["comp"] in SHARED_COMPONENTS:
        return "G4"
    return DOM_LANE[row["dom"]]


def route_open(open_: str) -> str:
    """开放项归属：转派 G4 / 跨域 / 本域自修。"""
    if re.search(r"service/common|users-common|共享层", open_):
        return "**→ G4**"
    if re.search(r"跨域|跨组件|跨栈", open_):
        return "跨域"
    return "本域自修"


# ── 文件生成 ────────────────────────────────────────────────────────────
def render(lane: str, rows) -> str:
    branch, scope = LANE_TITLE[lane]
    mine = [r for r in rows if lane_of(r) == lane]
    mine.sort(key=lambda r: (r["dom"] != "共享层", r["dom"], r["comp"]))

    ranks = [STATUS_RANK[r["st"]] for r in mine if r["st"] in STATUS_RANK]
    lane_status = next(k for k, v in STATUS_RANK.items() if v == min(ranks)) if ranks else "—"
    n_ok = sum(1 for r in mine if r["st"] == "✅")
    n_y = sum(1 for r in mine if r["st"] == "🟡")
    n_r = sum(1 for r in mine if r["st"] == "🔴")

    out: list[str] = []
    A = out.append
    A(f"# {lane} 阶段一验收矩阵（`{branch}`）")
    A("")
    A("> ⚠️ **本文件是机器生成的 —— 不要手改。**")
    A("> 生成器：`python3 tools/difftest/check-phasegoal-lanes.py --write`；"
      "校验器：`python3 tools/difftest/check-phasegoal-lanes.py`（CI 门禁）。")
    A("> **唯一数据源**：[`docs/PhaseGoal-judge.md`](../PhaseGoal-judge.md)（阶段一终点指标基线 v3）。")
    A("> ⇒ **改判定请改基线，然后重跑生成器** —— 手改本文件 ⇒ 与基线分叉 ⇒ 门禁红。")
    A("")
    A(f"- **lane 归属**（AGENTS.md §5.3d / §5.3f）：{scope}")
    A(f"- **组件数**：{len(mine)}（✅ {n_ok} · 🟡 {n_y} · 🔴 {n_r}）")
    A(f"- **lane 判定**：**{lane_status}**（= MIN(本 lane 组件状态)，序 ✅ > 🟡 > ⚫ > 🔴；"
      "口径唯一出处 = 基线 §8.1）")
    A("- **写权**：🔴 `service/common/**` + `service/users/users-common/**` **只有 G4 能写**；"
      "**域侧能自修的域侧修**，**共享层缺陷一律转派 G4**（反馈单 7 项 = §2.2）。")
    A("")
    A("---")
    A("")
    A("## §1 组件行（由基线 A-2 按 lane 过滤 ⇒ **不手抄**）")
    A("")
    A("| 域 | 组件 | 必达终点指标 | R | E | 状态 | 仍开放项 | 开放项归属 |")
    A("|---|---|---|---|---|---|---|---|")
    for r in mine:
        A(f"| `{r['dom']}` | `{r['comp']}` | {r['metric']} | **{r['r']}** | {r['e']} | {r['st']} "
          f"| {r['open_']} | {route_open(r['open_'])} |")
    A("")
    A("---")
    A("")
    A("## §2 自修清单 / 转派 G4")
    A("")
    A("### §2.1 本 lane 自修（域侧能自修的域侧修）")
    A("")
    own = [r for r in mine if route_open(r["open_"]) == "本域自修" and r["st"] != "✅"]
    if own:
        A("| 组件 | 开放项 | 证据强度 | 建议修法 |")
        A("|---|---|---|---|")
        for r in own:
            A(f"| `{r['comp']}` | {r['open_']} | {r['e']} | 见基线 A-2「权威来源」列的 `文件:行号` |")
    else:
        A("（无 —— 本 lane 组件行全部 ✅）")
    A("")
    A("### §2.2 转派 G4（**反馈单 7 项**：现象 / Go 侧 `文件:行号` / RAG 侧现状 / 证据强度 / "
      "建议修法 / 影响面 / 是否阻塞本波）")
    A("")
    to_g4 = [r for r in mine if route_open(r["open_"]) == "**→ G4**"]
    if to_g4:
        A("| 现象 | Go 侧 `文件:行号` | RAG 侧现状 | 证据强度 | 建议修法 | 影响面 | 是否阻塞本波 |")
        A("|---|---|---|---|---|---|---|")
        for r in to_g4:
            A(f"| {r['comp']}：{r['open_']} | 见基线 A-2「权威来源」列 | 落点在共享层，**G1~G3 只读** "
              f"| {r['e']} | 提 G4 落码 | 阻塞**共享层**（不阻塞本域组件） | 🟡 否 |")
    else:
        A("（本 lane 无转派项）")
    A("")
    A("---")
    A("")
    A("## §3 P0 九条分摊")
    A("")
    A("> 主责唯一；「协作」= 另一 lane 需配合的项。**收口执行者**负责的项不列入任何 lane。")
    A("")
    A("| P0 | 内容 | 本 lane 的责任 | 待补 / 现状 | 判定命令 |")
    A("|---|---|---|---|---|")
    for n in sorted(P0_MAP):
        owner, collab, title, gap, cmd = P0_MAP[n]
        if owner == lane:
            A(f"| **{n}** | {title} | **主责** | {gap} | {cmd} |")
        elif lane in collab:
            A(f"| **{n}** | {title} | 协作 | {gap} | {cmd} |")
    A("")
    owned_elsewhere = [f"#{n}（{P0_MAP[n][0]}）" for n in sorted(P0_MAP)
                       if P0_MAP[n][0] not in (lane,) and lane not in P0_MAP[n][1]]
    if owned_elsewhere:
        A(f"> 本 lane 不涉及：{' · '.join(owned_elsewhere)}")
        A("")
    A("---")
    A("")
    A("## §4 矩阵 C（跨组件不变量）分摊")
    A("")
    A("| X | 不变量 | 本 lane 的责任 | 现状 / 缺口 | 判定命令 |")
    A("|---|---|---|---|---|")
    for n in sorted(X_MAP):
        owner, collab, title, gap, cmd = X_MAP[n]
        if owner == lane:
            A(f"| **{n}** | {title} | **主责** | {gap} | {cmd} |")
        elif lane in collab:
            A(f"| **{n}** | {title} | 协作 | {gap} | {cmd} |")
    A("")
    owned_elsewhere_x = [f"X-{n}（{X_MAP[n][0]}）" for n in sorted(X_MAP)
                         if X_MAP[n][0] != lane and lane not in X_MAP[n][1]]
    if owned_elsewhere_x:
        A(f"> 本 lane 不涉及：{' · '.join(owned_elsewhere_x)}")
        A("")
    A("---")
    A("")
    A("## §5 收口验收命令（**全部可复算**；覆盖范围见基线 §六）")
    A("")
    A("```bash")
    for c in LANE_COMMANDS[lane]:
        A(c)
    A("```")
    A("")
    A("---")
    A("")
    A("## §6 lane 判定")
    A("")
    A(f"| 项 | 值 |")
    A("|---|---|")
    A(f"| 组件行 | {len(mine)} 个（✅ {n_ok} · 🟡 {n_y} · 🔴 {n_r}） |")
    A(f"| **lane 判定** | **{lane_status}** |")
    A(f"| 达标条件 | 本 lane 组件**全部 ✅** 且 归属本 lane 的必相对齐项 = 0（基线 §1.3） |")
    A(f"| 差距 | {n_y + n_r} 个组件未达标（🟡 {n_y} · 🔴 {n_r}） |")
    A("")
    A("> ⚠️ **lane 判定只覆盖组件层**；矩阵 C 与 P0 是**收口执行者**的判定（基线 §八）"
      "⇒ **本 lane 绿 ≠ 阶段一绿**。")
    A("")
    A("---")
    A("")
    A("## §7 本 lane 的交付物")
    A("")
    for d in LANE_DOMAINS[lane]:
        p = f"docs/DRIFT/{d}/vs-common.md"
        exists = (ROOT / p).exists()
        A(f"- [`{p}`](../DRIFT/{d}/vs-common.md) —— **9 条机制面轴（A1~A9）逐轴结论**"
          f"（{'✅ 已落盘' if exists else '❌ 缺失'}）")
    if lane == "G4":
        A("- [`docs/DRIFT/global/vs-common.md`](../DRIFT/global/vs-common.md) —— 共享层对账")
        A("- [`docs/DRIFT/common/alignment-2026-10-23.md`](../DRIFT/common/alignment-2026-10-23.md) "
          "—— 共享层主台账（**27 条编号未修**）")
    A("")
    return "\n".join(out)


def main() -> int:
    if not BASE.exists():
        print(f"❌ 找不到基线 {BASE}")
        return 1
    rows = read_baseline_rows()
    if len(rows) != 39:
        fail("基线解析", f"A-2 应有 39 行，实际解析出 {len(rows)} 行 ⇒ 基线结构变了，本工具要跟着改")
        return report()

    per_lane = {lane: [r for r in rows if lane_of(r) == lane] for lane in LANES}
    expect_counts = {"G1": 10, "G2": 11, "G3": 16, "G4": 2}
    for lane, n in expect_counts.items():
        got = len(per_lane[lane])
        if got != n:
            fail("lane 组件数", f"{lane} 期望 {n} 个组件，实际 {got} 个")

    # 归属表覆盖性：每条 P0 / X 有且仅有一个主责
    for n, (owner, collab, *_rest) in P0_MAP.items():
        if owner not in LANES and owner != "收口":
            fail("P0 归属", f"P0 #{n} 的主责 {owner!r} 非法")
    for n, (owner, collab, *_rest) in X_MAP.items():
        if owner not in LANES and owner != "收口":
            fail("X 归属", f"X-{n} 的主责 {owner!r} 非法")
        if owner in LANES and owner in collab:
            fail("X 归属", f"X-{n} 的主责 {owner} 同时出现在协作列表里")

    # 命令路径必须存在
    for lane, cmds in LANE_COMMANDS.items():
        for c in cmds:
            for m in re.finditer(r"(?<![\w/.-])(tools/[A-Za-z0-9_\-./]+)", c):
                p = m.group(1)
                if "<" in p or ">" in p:
                    continue
                if not (ROOT / p).exists():
                    fail("命令路径", f"{lane} 的 §5 引用了**仓内不存在**的路径：{p}")

    # 生成 / 校验
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for lane in LANES:
        text = render(lane, rows)
        path = OUT_DIR / f"{lane.lower()}.md"
        if "--write" in sys.argv:
            path.write_text(text, encoding="utf-8")
            note(f"{path.relative_to(ROOT)} 已写入（{len(text.encode('utf-8'))} B）")
        else:
            if not path.exists():
                fail("文件缺失", f"{path.relative_to(ROOT)} 不存在（先跑 --write）")
            elif path.read_text(encoding="utf-8") != text:
                fail("内容分叉", f"{path.relative_to(ROOT)} 与基线推导结果不一致 ⇒ 有人手改了它，或基线变了")

    # 每个 lane 文件里必须出现该 lane 的全部组件
    for lane in LANES:
        path = OUT_DIR / f"{lane.lower()}.md"
        if not path.exists():
            continue
        body = path.read_text(encoding="utf-8")
        for r in per_lane[lane]:
            if f"`{r['comp']}`" not in body:
                fail("组件覆盖", f"{path.name} 里缺组件 `{r['comp']}`")

    return report()


def report() -> int:
    if "--verbose" in sys.argv:
        for n in NOTES:
            print(f"· {n}")
    if FAILURES:
        print(f"❌ per-lane 阶段一验收矩阵自检：{len(FAILURES)} 项不通过")
        for f in FAILURES:
            print(f"  ❌ {f}")
        return 1
    print("✅ per-lane 阶段一验收矩阵自检：全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
