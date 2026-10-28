#!/usr/bin/env python3
"""从 **Go 真源** 的 follow.proto 生成「proto 契约基准」文件。

为什么要有这个脚本（AGENTS.md §3.4 + §3.3）：
  - `.proto` 文件两侧一致 ≠ 生成码的**契约**（字段号 / 类型 / 枚举值 / rpc 签名）一致；
  - 手抄一份「期望字段表」= 把真源的**实现细节**抄一遍 ⇒ 抄错时看不出错
    （「测试的期望值必须由真源生成」这条教训）；
  - ⇒ 期望值**由脚本从真源 .proto 生成**，冻结进仓，测试只做「基准 ↔ 生成码描述符」的比对。

用法：
    python3 generate.py <go-proto-path> <output-txt> [go-repo-root]

例：
    python3 generate.py \
      "/Users/pr/Documents/java&golang/Sea-RideTheWind_golang/proto/follow.proto" \
      follow-proto-contract.expected.txt \
      "/Users/pr/Documents/java&golang/Sea-RideTheWind_golang"

⇒ 第三个参数是**可选**的：给了就把「真源路径」记成 `<仓库名>/<仓库内相对路径>`
   （避免把本机绝对路径写进仓）；sha256 才是真正钉住内容的东西。

⚠️ **基准文件不是手工编辑物**：真源 .proto 变了 ⇒ 重新跑脚本 ⇒ 重新评审 diff。
   绝不要直接改 expected.txt（那会把「真源变了」这件事藏起来）。

🔴 **为什么 follow 域要有自己的脚本，而不是复用 hot 域的那份**
（2026-10-29 · G1 lane · 阶段 1 实测）：

  hot 域的 `generate.py` 用**非贪婪正则**抓 message 体：
      message[ \\t]+(\\w+)[ \\t]*\\{(.*?)\\}
  ⇒ 遇到**嵌套 message** 时，会在**内层**的 `}` 处截断。
  ⇒ 对 follow.proto 实测产出：
      message|RecommendResp|code=1:int32|msg=2:string|target_id=1:int64|mutual_score=2:int32
  —— 内层 `RecommendUser` 的字段被**平铺进父级**，
     而父级自己的 `users=3:repeated RecommendUser` **整条丢失**。
  ⇒ 这样的基准 = **假契约**（它断言的契约与实际生成码不同），
     且**恰好把本域最该钉住的那条（嵌套 message 的字段号）放过了**。

  ⇒ 本脚本改用**括号配对**扫描 + **递归**解析嵌套 message，
     产出 `message|<父>.<嵌套>|...` 的独立行 ⇒ 与生成码的
     `Descriptor#getNestedTypes()` 一一对应。

输出格式（每行一条，行内字段用 '|' 分隔，顺序即声明顺序）：
    # <header comments>
    package <proto package>
    enum|<EnumName>|<NAME>=<num>|<NAME>=<num>...
    message|<MessageName>|<field>=<num>:<type>|<field>=<num>:<type>...
    message|<MessageName>.<NestedMessageName>|<field>=<num>:<type>...
    service|<ServiceName>|<Method>(<In>)-><Out>|...
"""

import hashlib
import os
import re
import sys

# 字段行： [label] <type> <name> = <num>;
FIELD_RE = re.compile(
    r"^[ \t]*(?:(optional|repeated)[ \t]+)?"
    r"(map[ \t]*<[ \t]*[\w.]+[ \t]*,[ \t]*[\w.]+[ \t]*>|[\w.]+)[ \t]+"
    r"(\w+)[ \t]*=[ \t]*(\d+)[ \t]*;",
    re.M,
)
MAP_RE = re.compile(r"^map[ \t]*<[ \t]*([\w.]+)[ \t]*,[ \t]*([\w.]+)[ \t]*>$")

# 顶层块： message / enum / service
BLOCK_RE = re.compile(r"\b(message|enum|service)[ \t]+(\w+)[ \t]*\{")
# 嵌套块：只认 message
NESTED_RE = re.compile(r"\bmessage[ \t]+(\w+)[ \t]*\{")


def strip_comments(text):
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"//[^\n]*", "", text)
    return text


def normalize_type(raw):
    """把 .proto 里的类型写法归一成基准里的写法。"""
    t = re.sub(r"[ \t]+", " ", raw).strip()
    m = MAP_RE.match(t)
    if m:
        return "map<%s, %s>" % (m.group(1), m.group(2))
    return t


def match_body(text, brace_index):
    """``text[brace_index] == '{'`` ⇒ 返回 ``(块体, 配对 '}' 的下标)``。

    ⚠️ **必须用括号配对，不能用非贪婪正则** —— 非贪婪会在**内层**的 '}' 处截断，
    对**嵌套 message** 会解析错（见脚本头部 🔴 说明）。
    """
    depth = 0
    j = brace_index
    n = len(text)
    while j < n:
        c = text[j]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return text[brace_index + 1:j], j
        j += 1
    raise SystemExit("括号不配对（.proto 被截断？）")


def top_blocks(text):
    """按声明顺序取出**顶层**块 ⇒ ``[(kind, name, body), ...]``。

    ⇒ 嵌套 message 不会被当成顶层块（它在父块体内，已被整块跳过）。
    """
    blocks = []
    i = 0
    while True:
        m = BLOCK_RE.search(text, i)
        if not m:
            break
        body, close = match_body(text, m.end() - 1)
        blocks.append((m.group(1), m.group(2), body))
        i = close + 1
    return blocks


def split_nested(body):
    """把 message 体里的**嵌套 message 块**挖出来。

    返回 ``(本层剩余文本, [(嵌套名, 嵌套体), ...])``。
    ⇒ 本层剩余文本里**不再含**嵌套 message 的字段 ⇒ 本层字段不会被误收。
    """
    nested = []
    kept = []
    i = 0
    while True:
        m = NESTED_RE.search(body, i)
        if not m:
            kept.append(body[i:])
            break
        kept.append(body[i:m.start()])
        nbody, close = match_body(body, m.end() - 1)
        nested.append((m.group(1), nbody))
        i = close + 1
    return "".join(kept), nested


def parse_fields(text):
    """取本层字段 ⇒ ``["name=num:type", ...]``（声明顺序）。"""
    fields = []
    for fm in FIELD_RE.finditer(text):
        label, ftype, fname, fnum = fm.groups()
        ftype = normalize_type(ftype)
        if label == "optional":
            token = "optional " + ftype
        elif label == "repeated":
            token = "repeated " + ftype
        else:
            token = ftype
        fields.append("%s=%s:%s" % (fname, fnum, token))
    return fields


def render_message(name, body, lines):
    """渲染一个 message（**递归**）⇒ 父行 + 紧随其后的嵌套行。"""
    own, nested = split_nested(body)
    fields = parse_fields(own)
    lines.append("message|%s|%s" % (name, "|".join(fields)))
    for nname, nbody in nested:
        render_message("%s.%s" % (name, nname), nbody, lines)


def main(argv):
    if len(argv) not in (3, 4):
        raise SystemExit(__doc__)
    src, out = argv[1], argv[2]
    go_repo = argv[3] if len(argv) == 4 else None
    with open(src, "rb") as fh:
        raw_bytes = fh.read()
    raw = raw_bytes.decode("utf-8")
    sha = hashlib.sha256(raw_bytes).hexdigest()

    src_label = src
    if go_repo:
        try:
            rel = os.path.relpath(src, go_repo)
            src_label = "%s/%s" % (os.path.basename(os.path.normpath(go_repo)), rel)
        except ValueError:
            src_label = src

    text = strip_comments(raw)

    pkg_m = re.search(r"^[ \t]*package[ \t]+([\w.]+)[ \t]*;", text, flags=re.M)
    if not pkg_m:
        raise SystemExit("找不到 package 声明")
    pkg = pkg_m.group(1)

    enums = []
    messages = []
    services = []
    for kind, name, body in top_blocks(text):
        if kind == "enum":
            vals = ["%s=%s" % (vm.group(1), vm.group(2))
                    for vm in re.finditer(r"(\w+)[ \t]*=[ \t]*(-?\d+)[ \t]*;", body)]
            enums.append((name, vals))
        elif kind == "message":
            messages.append((name, body))
        elif kind == "service":
            methods = ["%s(%s)->%s" % (rm.group(1), rm.group(2), rm.group(3))
                       for rm in re.finditer(
                           r"rpc[ \t]+(\w+)[ \t]*\([ \t]*([\w.]+)[ \t]*\)"
                           r"[ \t]*returns[ \t]*\([ \t]*([\w.]+)[ \t]*\)", body)]
            services.append((name, methods))

    lines = [
        "# proto 契约基准 —— **由脚本从 Go 真源生成，勿手工编辑**",
        "# 生成脚本：同目录 generate.py（用法见脚本头部）",
        "# 真源：%s" % src_label,
        "# 真源 sha256：%s" % sha,
        "package %s" % pkg,
    ]
    for name, vals in enums:
        lines.append("enum|%s|%s" % (name, "|".join(vals)))
    for name, body in messages:
        render_message(name, body, lines)
    for name, methods in services:
        lines.append("service|%s|%s" % (name, "|".join(methods)))

    with open(out, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")
    print("wrote %s (%d lines)" % (out, len(lines)))


if __name__ == "__main__":
    main(sys.argv)
