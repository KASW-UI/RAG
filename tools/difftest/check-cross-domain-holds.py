#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-cross-domain-holds.py —— **跨域单点守门**（global 收口域专用）

为什么需要这个脚本（出处：CYCLE-001 / CYCLE-002）
------------------------------------------------
2026-10-25，`service/common` 里有一个 `@Deprecated` 方法 `DeadlineState.markExpired(long)`，
它**被保留的唯一理由**是另一个域（users）的 javadoc 用 `{@link}` 链着它：

    service/common/.../RpcInterceptor.java:617   markExpired(long)   ← 为了 B 的链接而保留
            ↓
    service/users/user/user-rpc/.../UserDomainService.java:113
            「配合 {@link DeadlineState#markExpired(long)}」        ← 这句本身还是假的

⇒ **A 保留废弃方法是为了 B 的链接不断；B 的链接修好了，A 才能删** ⇒ 真循环。
⇒ 谁先动都留半截 ⇒ 必须同一次提交走完 ⇒ 这类条目只能由 `global` 收口域处置。

那个循环已于 2026-10-25 闭环（users 侧订正假断言 + common 侧删方法，同一提交）。
本脚本把「**同类隐患还有没有**」变成**可重跑的判定**，而不是一次性的人工结论。

本脚本检查三件事（任一硬规则命中 ⇒ EXIT=1）
------------------------------------------
① **依赖不倒置**：`service/common/**` 不得 `import com.rag.<非 common>`。
   （common 是所有业务模块的下游；它一旦反向依赖某个业务域，整棵依赖树就倒过来了。）

② **跨域 javadoc 链接**：`service/common/**` 的 javadoc 里**不得** `{@link}` 到
   **别的 `service/**` 模块**的类。
   （这正是 CYCLE-001 的形状：common 的文档指向别的域 ⇒ 那个域一改，链接就悬空，
     而 common 的生命周期与别的域无关 ⇒ 又一次跨域单点。）

③ **被 @Deprecated 保留、且被 service/common 之外引用**的方法 ⇒ 报告出来。
   （这是 CYCLE-001 的**精确形状**：废弃方法只要还有外部引用，就不能删。）

用法
----
    python3 tools/difftest/check-cross-domain-holds.py            # 跑守门
    python3 tools/difftest/check-cross-domain-holds.py --verbose  # 附带列出 @Deprecated 清单

EXIT=0 ⇒ 三条规则全部满足；EXIT=1 ⇒ 有硬规则命中（看输出）。
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
COMMON_MAIN = REPO_ROOT / "service" / "common" / "src" / "main" / "java"
SERVICE_DIR = REPO_ROOT / "service"

# service/common 的包前缀（其下的类算「自己人」）
COMMON_PKG_PREFIX = "com.rag.common"

# JDK / 标准库类型名白名单
# ⚠️ 为什么需要：仓库里可能存在与 JDK 同名的类（实测：`users/admin/admin-rpc` 里有一个
#    叫 `System` 的类）⇒ 若不排除，`{@link System}` 会被误判成「跨域链接」。
JDK_TYPE_NAMES = {
    # java.lang
    "Object", "String", "StringBuilder", "StringBuffer", "CharSequence", "Character",
    "Boolean", "Byte", "Short", "Integer", "Long", "Float", "Double", "Number",
    "Math", "System", "Thread", "ThreadLocal", "ThreadGroup", "Runnable", "Process",
    "ProcessBuilder", "Class", "ClassLoader", "Package", "Module", "Iterable",
    "Comparable", "Comparator", "Cloneable", "Serializable", "AutoCloseable",
    "Appendable", "Readable", "Void", "Enum", "Record", "Deprecated", "Override",
    "SuppressWarnings", "SafeVarargs", "FunctionalInterface", "Exception",
    "RuntimeException", "Error", "Throwable", "IllegalArgumentException",
    "IllegalStateException", "UnsupportedOperationException", "NullPointerException",
    "IndexOutOfBoundsException", "ArrayIndexOutOfBoundsException",
    "StringIndexOutOfBoundsException", "ClassCastException", "NumberFormatException",
    "ArithmeticException", "InterruptedException", "CloneNotSupportedException",
    "StackOverflowError", "OutOfMemoryError", "AssertionError", "NoSuchFieldException",
    "NoSuchMethodException", "ReflectiveOperationException", "SecurityException",
    "UnsupportedClassVersionError", "LinkageError", "ClassNotFoundException",
    "InstantiationException", "IllegalAccessException", "NoSuchMethodError",
    # java.util
    "List", "ArrayList", "LinkedList", "Map", "HashMap", "LinkedHashMap", "TreeMap",
    "ConcurrentHashMap", "ConcurrentMap", "NavigableMap", "SortedMap", "Set", "HashSet",
    "LinkedHashSet", "TreeSet", "SortedSet", "NavigableSet", "Collection", "Collections",
    "Arrays", "Objects", "Optional", "OptionalInt", "OptionalLong", "OptionalDouble",
    "Iterator", "ListIterator", "Comparator", "Queue", "Deque", "ArrayDeque",
    "PriorityQueue", "Vector", "Stack", "Hashtable", "Properties", "Random",
    "UUID", "Date", "Calendar", "TimeZone", "SimpleTimeZone", "BitSet", "Locale",
    "StringJoiner", "EnumMap", "EnumSet", "Scanner", "Timer", "TimerTask",
    "ConcurrentLinkedQueue", "ConcurrentLinkedDeque", "CopyOnWriteArrayList",
    "CopyOnWriteArraySet", "AbstractMap", "AbstractList", "AbstractSet", "WeakHashMap",
    "IdentityHashMap", "StringTokenizer", "NoSuchElementException",
    "ConcurrentModificationException", "EmptyStackException", "MissingResourceException",
    # java.io / java.nio / java.net / java.time / java.text / java.math
    "InputStream", "OutputStream", "Reader", "Writer", "File", "FileInputStream",
    "FileOutputStream", "FileReader", "FileWriter", "BufferedReader", "BufferedWriter",
    "ByteArrayInputStream", "ByteArrayOutputStream", "InputStreamReader",
    "OutputStreamWriter", "PrintWriter", "PrintStream", "IOException", "FileNotFoundException",
    "UncheckedIOException", "Serializable", "Closeable", "Flushable", "DataInputStream",
    "DataOutputStream", "ObjectInputStream", "ObjectOutputStream", "RandomAccessFile",
    "Path", "Paths", "Files", "StandardCharsets", "StandardOpenOption", "ByteBuffer",
    "CharBuffer", "Charset", "CharsetDecoder", "CharsetEncoder", "URI", "URL", "URLEncoder",
    "URLDecoder", "InetAddress", "Socket", "ServerSocket", "SocketException", "MalformedURLException",
    "Duration", "Instant", "LocalDate", "LocalDateTime", "LocalTime", "ZonedDateTime",
    "OffsetDateTime", "OffsetTime", "ZoneId", "ZoneOffset", "DateTimeFormatter",
    "DateTimeFormatterBuilder", "Period", "Duration", "Temporal", "TemporalUnit",
    "SimpleDateFormat", "DateFormat", "NumberFormat", "DecimalFormat", "MessageFormat",
    "BigDecimal", "BigInteger", "StrictMath", "Random", "UUID", "Currency",
    "AtomicInteger", "AtomicLong", "AtomicBoolean", "AtomicReference", "AtomicIntegerArray",
    "AtomicLongArray", "AtomicReferenceArray", "LongAdder", "LongAccumulator",
    "DoubleAdder", "CopyOnWriteArrayList", "ConcurrentSkipListMap", "ConcurrentSkipListSet",
    "Executor", "ExecutorService", "Executors", "ScheduledExecutorService",
    "ScheduledFuture", "ScheduledThreadPoolExecutor", "Future", "FutureTask",
    "CompletableFuture", "CompletionStage", "TimeUnit", "TimeoutException",
    "ExecutionException", "RejectedExecutionException", "ThreadPoolExecutor",
    "ThreadFactory", "BlockingQueue", "LinkedBlockingQueue", "ArrayBlockingQueue",
    "SynchronousQueue", "CountDownLatch", "CyclicBarrier", "Semaphore", "Phaser",
    "Exchanger", "LockSupport", "AbstractExecutorService", "ThreadLocalRandom",
    "ForkJoinPool", "ForkJoinTask", "Callable", "BlockingDeque", "DelayQueue",
    "PriorityBlockingQueue", "LinkedBlockingDeque", "RejectedExecutionHandler",
    "ConcurrentModificationException", "IllegalMonitorStateException", "WeakReference",
    "SoftReference", "PhantomReference", "ReferenceQueue", "WeakHashMap",
    "InvocationHandler", "Method", "Field", "Constructor", "Modifier", "Array",
    "Proxy", "InheritableThreadLocal", "UncaughtExceptionHandler",
    "ThreadLocal", "ScopedValue", "Runtime", "ProcessHandle", "ClassValue",
}

# {@link Xxx#member(...)} / {@link Xxx} / {@linkplain Xxx}
LINK_RE = re.compile(r"\{@link(?:plain)?\s+([A-Za-z_][A-Za-z0-9_]*)")
# import com.rag.xxx.yyy.Zzz;
IMPORT_RE = re.compile(r"^\s*import\s+(com\.rag\.[A-Za-z0-9_.]+)\s*;", re.MULTILINE)
# @Deprecated 后紧跟的方法声明
DEPRECATED_RE = re.compile(
    r"@Deprecated\s+(?:@[\w.]+(?:\([^)]*\))?\s+)*"
    r"(?:public|protected|private|static|final|synchronized|abstract|native|default|strictfp|\s)*"
    r"[\w<>\[\],.?\s]+?\s+(\w+)\s*\(",
    re.MULTILINE,
)
# 类/接口/枚举声明
TYPE_DECL_RE = re.compile(
    r"\b(?:public\s+|final\s+|abstract\s+|sealed\s+|non-sealed\s+|static\s+)*"
    r"(?:class|interface|enum|record)\s+(\w+)"
)


def java_files(root: Path) -> list[Path]:
    return sorted(p for p in root.rglob("*.java") if "target" not in p.parts)


def build_repo_type_index() -> dict[str, list[str]]:
    """全仓类型名 → 所在模块（相对 service/ 的路径）。

    ⚠️ 只索引 **本仓** 的类型 ⇒ 第三方类（Spring / Jackson / grpc）自然不在其中
    ⇒ 规则 ② 不会误报第三方 `{@link}`。
    """
    index: dict[str, list[str]] = {}
    for f in java_files(SERVICE_DIR):
        src = f.read_text(encoding="utf-8", errors="replace")
        mod = str(f.parent.relative_to(SERVICE_DIR))
        for m in TYPE_DECL_RE.finditer(src):
            index.setdefault(m.group(1), []).append(mod)
    return index


def common_own_types() -> set[str]:
    own: set[str] = set()
    for f in java_files(COMMON_MAIN):
        src = f.read_text(encoding="utf-8", errors="replace")
        own.update(m.group(1) for m in TYPE_DECL_RE.finditer(src))
    return own


def check_no_reverse_dependency() -> list[str]:
    """规则 ①：service/common 不得 import 别的业务域。"""
    bad: list[str] = []
    for f in java_files(COMMON_MAIN):
        for m in IMPORT_RE.finditer(f.read_text(encoding="utf-8", errors="replace")):
            imp = m.group(1)
            if not imp.startswith(COMMON_PKG_PREFIX):
                rel = f.relative_to(REPO_ROOT)
                bad.append(f"{rel}: import {imp}")
    return bad


def check_cross_domain_links() -> list[str]:
    """规则 ②：service/common 的 javadoc 不得 {@link} 到别的 service 模块的类。"""
    index = build_repo_type_index()
    own = common_own_types()
    bad: list[str] = []
    for f in java_files(COMMON_MAIN):
        src = f.read_text(encoding="utf-8", errors="replace")
        rel = f.relative_to(REPO_ROOT)
        for m in LINK_RE.finditer(src):
            name = m.group(1)
            if name in own or name in JDK_TYPE_NAMES:
                continue
            modules = index.get(name)
            if not modules:
                continue  # 第三方类（Spring/Jackson/grpc…）⇒ 不归本规则管
            # 只在「该类型存在于某个非 common 模块、且不存在于 common」时才算跨域链接
            if all(mod.startswith("common") for mod in modules):
                continue
            bad.append(f"{rel}: {{@link {name}}} → 定义在 {sorted(set(modules))}")
    return bad


def check_deprecated_referenced_outside(verbose: bool) -> list[str]:
    """规则 ③：被 @Deprecated 保留、且被 service/common 之外引用的方法。

    返回仅供参考的清单（不判失败）。
    ⚠️ 这条**不是硬失败**：外部引用本身不是错（可能是合法的兼容层），
       但它意味着「这个废弃方法不能删」⇒ 必须有人知道 ⇒ 所以列出来。
    """
    deprecated: list[tuple[str, str]] = []
    for f in java_files(COMMON_MAIN):
        src = f.read_text(encoding="utf-8", errors="replace")
        rel = str(f.relative_to(REPO_ROOT))
        for m in DEPRECATED_RE.finditer(src):
            deprecated.append((rel, m.group(1)))

    soft: list[str] = []
    for rel, method in deprecated:
        pattern = re.compile(r"\b" + re.escape(method) + r"\b")
        hits: list[str] = []
        for f in java_files(SERVICE_DIR):
            frel = str(f.relative_to(REPO_ROOT))
            if frel.startswith("service/common/src/main/java/"):
                continue
            if pattern.search(f.read_text(encoding="utf-8", errors="replace")):
                hits.append(frel)
        if hits:
            soft.append(f"{rel}: @{method} 被 service/common 之外引用（{len(hits)} 处）⇒ 不可删 ⇒ 归 global 处置")
    return soft


def main() -> int:
    ap = argparse.ArgumentParser(description="跨域单点守门（CYCLE-001/002 的复发防护）")
    ap.add_argument("--verbose", action="store_true", help="附带列出 @Deprecated 清单")
    args = ap.parse_args()

    if not COMMON_MAIN.exists():
        print(f"FATAL: 找不到 {COMMON_MAIN}", file=sys.stderr)
        return 2

    failures: list[str] = []
    failures += check_no_reverse_dependency()
    failures += check_cross_domain_links()
    deprecated_notes = check_deprecated_referenced_outside(args.verbose)

    print("=== 跨域单点守门（global 收口域）===")
    print("规则① 依赖不倒置（common 不得 import 业务域）")
    print("规则② 无跨域 javadoc 链接（common 不得 {@link} 别的 service 模块的类）")
    print("规则③ @Deprecated + 外部引用 ⇒ 列出来（不判失败，但要有人知道）")
    print()

    if deprecated_notes:
        print("【规则③ 提示】")
        for n in deprecated_notes:
            print("  - " + n)
        print()

    if failures:
        print("【硬规则命中】")
        for f in failures:
            print("  ✗ " + f)
        print()
        print("RESULT: FAIL")
        return 1

    print("RESULT: PASS（无跨域单点）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
