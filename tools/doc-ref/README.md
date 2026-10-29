# `tools/doc-ref/` —— 文档行号引用维护工具

## 为什么需要它

G-12 提交（`3222fa99`）给 7 份台账各加了「当前态指针」块：

| 目标台账 | 基线 → HEAD 行数变化 |
|---|---|
| `docs/DRIFT/users/common.md` | +5 |
| `docs/DRIFT/users/user/api.md` | +5 |
| `docs/DRIFT/users/user/rpc.md` | +5 |
| `docs/DRIFT/users/admin/api.md` | +5 |
| `docs/DRIFT/users/admin/rpc.md` | +5 |
| `docs/DRIFT/users/vs-common.md` | +270 |
| `docs/DRIFT/like/api.md` | +262 |

⇒ 仓内其它文档对这些台账的 `文件.md:行号` 引用整体过期。

## 工具

| 脚本 | 作用 |
|---|---|
| `fix_line_refs.py` | **改写器**。把过期的 `文件.md:行号` 引用平移到当前行号。默认预览，`--apply` 才落盘。 |
| `verify_line_refs.py` | **校验器**。逐处核对改写：基线行的内容是否落在新行号上；「指向错误」数必须为 0。 |

## 用法

```bash
python3 tools/doc-ref/fix_line_refs.py            # 预览
python3 tools/doc-ref/fix_line_refs.py --apply    # 落盘
python3 tools/doc-ref/verify_line_refs.py         # 核对（0 指向错误 = 通过）
```

## 三条设计约束（每条都对应一次真实踩坑）

### ① 目标解析必须走 markdown 约定，**不许后缀猜测**

旧版实现见到裸 `rpc.md` 就回退到「第一个以 `/rpc.md` 结尾的目标文件」⇒ 解析成
`docs/DRIFT/users/admin/rpc.md`。于是 `docs/DRIFT/favorite/rpc.md` 里指向**本域**
`rpc.md` 的自引用被整体误平移（实测 14 处）。

⇒ 本实现按 markdown 约定依次尝试：**相对引用文件所在目录 → 仓根 → `docs/DRIFT` →
`docs/DRIFT/users`**，且**必须解析到 G-12 的 7 个目标之一**才参与平移。

### ② 内容定位**不设行数窗口**

旧版用「±40 行」窗口找基准内容 ⇒ 偏移超过 40 行的（`like/api.md` 的 +71）一律
报「找不到」。⇒ 本实现用 `git diff` 的 hunk 头解析**行身份映射**（基线行 → 当前行），
兜底才用全文件内容搜索。

### ③ 多处命中 ⇒ **不改写**，登记待人工

空行 / 重复表头在台账里很常见。此时「基线行内容」在当前文件有多处命中，无法唯一定位
⇒ 瞎改比不改更糟（会把读者引到错误的行）。实测 15 处属此类：其中 **12 处基线行本身
是空行** ⇒ 那是**原引用本来就指错**（早于 G-12），**不是 G-12 引入的回归** ⇒ 不动。

## 可重复性

改写器**始终以 `HEAD` 快照为计算输入**（基线 = `3222fa99^`）⇒ 反复执行得到同一批结果，
不会二次平移；落盘用「旧串替换成新串」⇒ 第二次执行是**空操作**。

## 实跑记录（2026-10-29）

```
改写 56 处 / 21 个文件；目标归属终检：误配 0 处
verify_line_refs.py：内容一致 51 处 · 指向错误 0 处 · 无法判定 5 处
  （5 处是短路径 `admin/rpc.md` / `admin/api.md` ⇒ 解析不到唯一目标 ⇒ 不猜，
    人工核对偏移为 +5 ⇒ 正确）
```
