#!/usr/bin/env python3
"""从 **Go 真源** 的 .proto 生成「proto 契约基准」文件。

为什么要有这个脚本（AGENTS.md §3.4 + §3.3）：
  - `.proto` 文件两侧一致 ≠ 生成码的**契约**（字段号 / 类型 / 枚举值 / rpc 签名）一致；
  - 手抄一份「期望字段表」= 把真源的**实现细节**抄一遍 ⇒ 抄错时看不出错
    （见「测试的期望值必须由真源生成」这条教训）；
  - ⇒ 期望值**由脚本从真源 .proto 生成**，冻结进仓，测试只做「基准 ↔ 生成码描述符」的比对。

用法：
    python3 generate.py <go-proto-path> <output-txt> [go-repo-root]

例：
    python3 generate.py \
      "/Users/pr/Documents/java&golang/Sea-RideTheWind_golang/proto/article.proto" \
      article-proto-contract.expected.txt \
      "/Users/pr/Documents/java&golang/Sea-RideTheWind_golang"

⇒ 第三个参数是**可选**的：给了就把「真源路径」记成 `<仓库名>/<仓库内相对路径>`
   （避免把本机绝对路径写进仓）；sha256 才是真正钉住内容的东西。

⚠️ **基准文件不是手工编辑物**：真源 .proto 变了 ⇒ 重新跑本脚本 ⇒ 重新评审 diff。
   绝不要直接改 expected.txt（那会把「真源变了」这件事藏起来）。

输出格式（每行一条，行内字段用 '|' 分隔，顺序即声明顺序）：
    # <header comments>
    package <proto package>
    enum|<EnumName>|<NAME>=<num>|<NAME>=<num>...
    message|<MessageName>|<field>=<num>:<type>|<field>=<num>:<type>...
    service|<ServiceName>|<Method>(<In>)-><Out>|...
"""

import hashlib
import os
import re
import sys

SCALARS = {
    "double", "float", "int32", "int64", "uint32", "uint64", "sint32", "sint64",
    "fixed32", "fixed64", "sfixed32", "sfixed64", "bool", "string", "bytes",
}

# 字段行： [label] <type> <name> = <num>;
FIELD_RE = re.compile(
    r"^[ \t]*(?:(optional|repeated)[ \t]+)?"
    r"(map[ \t]*<[ \t]*[\w.]+[ \t]*,[ \t]*[\w.]+[ \t]*>|[\w.]+)[ \t]+"
    r"(\w+)[ \t]*=[ \t]*(\d+)[ \t]*;",
    re.M,
)
MAP_RE = re.compile(r"^map[ \t]*<[ \t]*([\w.]+)[ \t]*,[ \t]*([\w.]+)[ \t]*>$")


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


def parse(text):
    pkg_m = re.search(r"^[ \t]*package[ \t]+([\w.]+)[ \t]*;", text, flags=re.M)
    if not pkg_m:
        raise SystemExit("找不到 package 声明")
    pkg = pkg_m.group(1)

    enums = []
    for m in re.finditer(r"enum[ \t]+(\w+)[ \t]*\{(.*?)\}", text, flags=re.S):
        vals = ["%s=%s" % (vm.group(1), vm.group(2))
                for vm in re.finditer(r"(\w+)[ \t]*=[ \t]*(-?\d+)[ \t]*;", m.group(2))]
        enums.append((m.group(1), vals))

    messages = []
    for m in re.finditer(r"message[ \t]+(\w+)[ \t]*\{(.*?)\}", text, flags=re.S):
        fields = []
        for fm in FIELD_RE.finditer(m.group(2)):
            label, ftype, fname, fnum = fm.group(1), fm.group(2), fm.group(3), fm.group(4)
            ftype = normalize_type(ftype)
            if label == "optional":
                token = "optional " + ftype
            elif label == "repeated":
                token = "repeated " + ftype
            else:
                token = ftype
            fields.append("%s=%s:%s" % (fname, fnum, token))
        messages.append((m.group(1), fields))

    services = []
    for m in re.finditer(r"service[ \t]+(\w+)[ \t]*\{(.*?)\}", text, flags=re.S):
        methods = ["%s(%s)->%s" % (rm.group(1), rm.group(2), rm.group(3))
                   for rm in re.finditer(
                       r"rpc[ \t]+(\w+)[ \t]*\([ \t]*([\w.]+)[ \t]*\)"
                       r"[ \t]*returns[ \t]*\([ \t]*([\w.]+)[ \t]*\)", m.group(2))]
        services.append((m.group(1), methods))

    return pkg, enums, messages, services


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

    pkg, enums, messages, services = parse(strip_comments(raw))

    lines = [
        "# proto 契约基准 —— **由脚本从 Go 真源生成，勿手工编辑**",
        "# 生成脚本：同目录 generate.py（用法见脚本头部）",
        "# 真源：%s" % src_label,
        "# 真源 sha256：%s" % sha,
        "package %s" % pkg,
    ]
    for name, vals in enums:
        lines.append("enum|%s|%s" % (name, "|".join(vals)))
    for name, fields in messages:
        lines.append("message|%s|%s" % (name, "|".join(fields)))
    for name, methods in services:
        lines.append("service|%s|%s" % (name, "|".join(methods)))

    with open(out, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")
    print("wrote %s (%d lines)" % (out, len(lines)))


if __name__ == "__main__":
    main(sys.argv)
