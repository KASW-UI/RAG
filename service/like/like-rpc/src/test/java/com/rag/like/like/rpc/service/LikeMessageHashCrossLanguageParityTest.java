package com.rag.like.like.rpc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.like.like.rpc.config.Jackson2ObjectMapperConfiguration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>点赞消息语义哈希的 Go ↔ Java 双侧逐字节对拍</b> —— 阶段 1 的 P1 闭环证据。
 *
 * <h2>为什么这条值得单独做双侧探针（而不是写个 Java 单测就算）</h2>
 *
 * <p>{@code payload_hash} 会随 inbox 行<b>持久化</b>。同一条 {@code msg_id} 再次投递时，
 * {@code ProcessLikeMessageBatch} 会比对哈希：不等就报
 * {@code "like message %s reused with conflicting payload"} 并让整条消息重投。
 * ⇒ 两边只要算出<b>不同的字节序列</b>，灰度期就会出现「正常重投被当成 msg_id 被复用 ⇒ 消息卡死」。
 *
 * <p>而 {@code LikeMessageDigest} 的 javadoc 自己列了两个跨语言字节陷阱：
 * <ol>
 *   <li><b>字段顺序</b>：Go 的 {@code encoding/json} 按 struct 字段声明序输出，
 *       Jackson 的 {@code ObjectNode} 按插入序；</li>
 *   <li><b>HTML 转义</b>：Go <b>默认开启</b>（{@code <} → {@code \u003c}），
 *       Jackson <b>默认关闭</b>；Go 还把 U+2028/U+2029 转成 {@code \u2028}/{@code \u2029}，
 *       Jackson 不会。</li>
 * </ol>
 *
 * <p>⇒ 「源码逐条对得上」<b>不等于</b>「字节逐条相同」（AGENTS.md §3.3 的强制三条之二：
 * 凡写「与 Go 一致」必须有<b>两侧同时跑的探针</b>，非只跑 RAG）。
 *
 * <h2>本测试怎么保证「探的不是一份自我实现的副本」</h2>
 *
 * <ol>
 *   <li><b>被测函数是真源</b>：{@code like_message_hash.go} 是从 Go 仓库<b>逐字节复制</b>的，
 *       本测试比对两边的 SHA-256 ⇒ 复制过程没被改过；</li>
 *   <li><b>垫片不许漂移</b>：Go 探针用 {@code go/parser}（标准库）解析<b>真实</b>的
 *       {@code service/like/rpc/internal/model} 目录下的<b>全部非测试 {@code .go} 文件</b>，
 *       把 {@code LikeRecord} / {@code LikeProcessPayload} 的字段名与类型
 *       与垫片逐条比对 ⇒ 真源改了字段类型，探针会<b>报错退出</b>，而不是安静地比一份过期垫片；</li>
 *   <li><b>Java 侧用真实 bean</b>：{@code ObjectMapper} 取自
 *       {@link Jackson2ObjectMapperConfiguration}（生产 Bean 的工厂方法，非随手 new），
 *       哈希取自真实 {@link LikeMessageDigest}；</li>
 *   <li><b>样例覆盖三个陷阱</b>：{@code <>&}（HTML 转义）、U+2028/U+2029（Go 特有转义）、
 *       非 ASCII UTF-8（两侧都原样输出）。</li>
 * </ol>
 *
 * <h2>无 Go toolchain 时的行为</h2>
 *
 * <p>本测试<b>不会</b>因为环境缺 Go 而失败（{@link Assumptions} ⇒ 标记 skipped 并写明原因），
 * 但<b>钉死的向量</b>那条（{@code javaSideMatchesPinnedGoVectors}）永远执行 ⇒
 * 「Go 不在机器上」时仍有回归守门。
 *
 * <p>Go 出处：{@code service/like/rpc/internal/model/like_message_hash.go:9-27}（{@code likeMessageHash}）
 * · 结构体出处：{@code service/like/rpc/internal/model/like.go:37-43} 与同文件的 {@code LikeRecord}。
 */
class LikeMessageHashCrossLanguageParityTest {

    /** Go 真源里被测函数的文件（相对 Go 仓库根）。 */
    private static final String GO_HASH_SRC = "service/like/rpc/internal/model/like_message_hash.go";

    /** Go 真源里结构体定义的所在目录（相对 Go 仓库根）。 */
    private static final String GO_MODEL_DIR = "service/like/rpc/internal/model";

    /**
     * 对拍样例。
     *
     * <p>⚠️ 每一行都必须是<b>合法载荷</b>：{@code userId > 0}、{@code action ∈ [1,4]}、
     * {@code occurredAt} 非零 —— 因为 Go 的 {@code likeMessageHash} 本身不做这些校验
     * （校验在 {@code ProcessLikeMessageBatch} 里），但真实消息一定满足 ⇒ 样例取真实形态。
     */
    private static final List<Case> CASES = List.of(
            // ① 基准：纯 ASCII、常规点赞
            new Case(7L, "article", "t1", 9L, 1, 1_700_000_000L, true),
            // ② 取消点赞（action=2）· 首次点赞为 false
            new Case(7L, "article", "t1", 9L, 2, 1_700_000_001L, false),
            // ③ 点踩（action=3）/ 取消点踩（action=4）
            new Case(1234567890123L, "comment", "c-42", 0L, 3, 1_700_000_002L, false),
            new Case(1234567890123L, "comment", "c-42", 0L, 4, 1_700_000_003L, false),
            // ④ ⚠️ HTML 转义陷阱：Go 会把 < > & 写成 < > &
            new Case(7L, "article", "a<b>&c", 9L, 1, 1_700_000_004L, true),
            // ⑤ ⚠️ Go 特有转义：U+2028 / U+2029
            new Case(7L, "article", "x\u2028y\u2029z", 9L, 1, 1_700_000_005L, false),
            // ⑥ 非 ASCII（UTF-8 原样输出，两侧都不转义）
            new Case(7L, "article", "文章标题", 9L, 1, 1_700_000_006L, true),
            // ⑦ JSON 自身的转义：引号与反斜杠
            new Case(7L, "article", "a\"b\\c", 9L, 1, 1_700_000_007L, false),
            // ⑧ 空串（target_type 合法但 target 为空 —— Go 侧不校验，取边界值）
            new Case(7L, "article", "", 9L, 1, 1_700_000_008L, false),
            // ⑨ 负数 occurredAt / 大整数 userId
            new Case(Long.MAX_VALUE, "article", "t9", Long.MIN_VALUE, 1, -1L, false));

    /**
     * Go 侧产出的<b>钉死向量</b>（由 {@code #goSideProducesSameHashAsJavaSide()} 实跑真源生成）。
     *
     * <p>用途：机器上没有 Go toolchain 时，本测试类仍然有一条<b>不依赖外部工具链</b>的守门
     * —— 任何人改了 {@code LikeMessageDigest} / {@code GoCompatibleJson} 的序列化行为，
     * 这条就会红。
     */
    private static final Map<String, String> PINNED_GO_VECTORS = Map.ofEntries(
            Map.entry("0", "983f9bcb10ac8b331481b17c575bb54bbf8b294a61ea45482f126454ff18b5c9"),
            Map.entry("1", "4a1c56b36d3769b30317889a0fc1b29424875a81da3be179ffd1dff71f6bb0fc"),
            Map.entry("2", "166138e7352b835fd2b3f3f62c6d3a3803720ba15817077d2a64b6b4fc788be3"),
            Map.entry("3", "6278dfe97a4bf7cb4dd4df49ae89b20d419bf66f45ddbc5f27ff0176fb2fc07e"),
            Map.entry("4", "74b7b7490a444198e8517fb0064100d0aad756c81def2bbc4899fd06b9ed040d"),
            Map.entry("5", "11960ad31a4b147c8aa1c38aeab1c7b94aa2c40b4a7344b46bbc335b0120975a"),
            Map.entry("6", "d871b848ca4835894ec7bf2892ec802f686363321482f83b129e002a47d581bc"),
            Map.entry("7", "b788e3652c6c30d5c777b4a35f4f6384f5ffc72ee98131bafb470a1d65f4c467"),
            Map.entry("8", "8fc98cca4b86d67be59f6119cd5d58e616f8102d67862edf584c9bb602af73ab"),
            Map.entry("9", "818ef4a52d4c744eca696ebb4559bdfe688c2b5390f8cb397b7cfff8110a282d"));

    @Test
    @DisplayName("P1 闭环：payload_hash 的 Go 真源 ↔ RAG 真实 bean 逐字节一致（含 HTML 转义 / U+2028 陷阱样例）")
    void goSideProducesSameHashAsJavaSide() throws Exception {
        Path goRepo = resolveGoRepo();
        Assumptions.assumeTrue(goRepo != null,
                "找不到 Go 真源仓库（用 GO_REPO=... 指定），本条双侧对拍跳过");
        Assumptions.assumeTrue(isGoToolchainAvailable(),
                "机器上没有 Go toolchain，本条双侧对拍跳过（钉死向量那条仍然执行）");

        Path hashSrc = goRepo.resolve(GO_HASH_SRC);
        Path modelDir = goRepo.resolve(GO_MODEL_DIR);
        assertTrue(Files.isRegularFile(hashSrc), "Go 真源缺失：" + hashSrc);
        assertTrue(Files.isDirectory(modelDir), "Go 真源目录缺失：" + modelDir);

        Map<String, String> goHashes = runGoProbe(hashSrc, modelDir);

        LikeMessageDigest digest = productionDigest();
        assertEquals(CASES.size(), goHashes.size(), "Go 侧返回的样例数与输入不一致");
        for (int i = 0; i < CASES.size(); i++) {
            Case c = CASES.get(i);
            String key = Integer.toString(i);
            String goHash = goHashes.get(key);
            assertTrue(goHash != null && !goHash.isBlank(), "Go 侧缺少样例 " + key);
            String javaHash = digest.hash(c.userId, c.targetType, c.targetId, c.authorId, c.action, c.occurredAt, c.isFirst);
            assertEquals(goHash, javaHash,
                    "payload_hash 两侧不一致 —— 样例 #" + key + "（targetId=" + c.targetId + "）"
                            + " ⇒ 灰度期同 msg_id 重投会被当成「ID 被复用」而卡死");
        }
    }

    @Test
    @DisplayName("回归守门：RAG 侧 payload_hash 仍等于 Go 真源产出的钉死向量")
    void javaSideMatchesPinnedGoVectors() {
        LikeMessageDigest digest = productionDigest();
        for (int i = 0; i < CASES.size(); i++) {
            Case c = CASES.get(i);
            String expected = PINNED_GO_VECTORS.get(Integer.toString(i));
            assertTrue(expected != null, "钉死向量缺少样例 " + i);
            String actual = digest.hash(c.userId, c.targetType, c.targetId, c.authorId, c.action, c.occurredAt, c.isFirst);
            assertEquals(expected, actual, "payload_hash 与 Go 真源向量不一致 —— 样例 #" + i);
        }
    }

    @Test
    @DisplayName("⚠️ 生产 Bean 的 ObjectMapper 与随手 new 的 ObjectMapper 在【序列化方向】必须同结果")
    void productionMapperAndPlainMapperAgree() {
        Case c = CASES.get(4); // 含 < > &
        LikeMessageDigest production = productionDigest();
        LikeMessageDigest plain = new LikeMessageDigest(new ObjectMapper());
        assertEquals(production.hash(c.userId, c.targetType, c.targetId, c.authorId, c.action, c.occurredAt, c.isFirst),
                plain.hash(c.userId, c.targetType, c.targetId, c.authorId, c.action, c.occurredAt, c.isFirst),
                "生产 Bean 与默认 ObjectMapper 的序列化行为不一致 ⇒ 钉死向量对哪一个生效将不可预测");
    }

    // ───────────────────────────── 内部工具 ─────────────────────────────

    /** 生产用的 {@link ObjectMapper}：取自生产 Bean 的工厂方法（不是随手 new 一个）。 */
    private static LikeMessageDigest productionDigest() {
        return new LikeMessageDigest(new Jackson2ObjectMapperConfiguration().objectMapper());
    }

    /**
     * 解析 Go 真源仓库的位置。
     *
     * <p>顺序：{@code GO_REPO} 环境变量 → 从本模块目录向上逐级找
     * {@code <祖先>/Sea-RideTheWind_golang} 与 {@code <祖先>/../Sea-RideTheWind_golang}
     * ⇒ 覆盖「主仓」与「{@code RAG.worktrees/} 下的工作树」两种布局。
     */
    private static Path resolveGoRepo() {
        String env = System.getenv("GO_REPO");
        if (env != null && !env.isBlank()) {
            Path p = Paths.get(env).toAbsolutePath().normalize();
            return Files.isDirectory(p) ? p : null;
        }
        Path cur = Paths.get("").toAbsolutePath().normalize();
        for (Path p = cur; p != null; p = p.getParent()) {
            for (Path candidate : List.of(p.resolve("Sea-RideTheWind_golang"), p.getParent() == null
                    ? null : p.getParent().resolve("Sea-RideTheWind_golang"))) {
                if (candidate != null && Files.isDirectory(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static boolean isGoToolchainAvailable() {
        try {
            Process p = new ProcessBuilder("go", "version").redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /**
     * 在临时目录里搭一个<b>零外部依赖</b>的 Go 模块，跑真源里的哈希函数。
     *
     * <p>三件套：
     * <ol>
     *   <li>{@code model/like_message_hash.go} = 真源<b>逐字节复制</b>（SHA-256 先验一遍）；</li>
     *   <li>{@code model/shim.go} = 只声明哈希函数真正读到的字段（垫片）；</li>
     *   <li>{@code model/probe.go} = 用 {@code go/parser} 把<b>真实</b>的 {@code like.go} 解析出来，
     *       逐条核对垫片的字段名与类型 ⇒ 真源改了字段，探针直接失败。</li>
     * </ol>
     */
    private static Map<String, String> runGoProbe(Path hashSrc, Path modelDir) throws Exception {
        Path tmp = Files.createTempDirectory("like-hash-parity-");
        try {
            Path model = tmp.resolve("model");
            Path main = tmp.resolve("main");
            Files.createDirectories(model);
            Files.createDirectories(main);

            // ① 真源逐字节复制 + SHA-256 校验
            Path copied = model.resolve("like_message_hash.go");
            byte[] raw = Files.readAllBytes(hashSrc);
            Files.write(copied, raw);
            assertEquals(sha256(raw), sha256(Files.readAllBytes(copied)), "复制到临时模块的哈希文件与真源不一致");

            Files.writeString(tmp.resolve("go.mod"), "module probe\n\ngo 1.25\n");
            Files.writeString(model.resolve("shim.go"), SHIM_GO);
            Files.writeString(model.resolve("probe.go"), PROBE_GO);
            Files.writeString(main.resolve("main.go"), MAIN_GO);

            // ② 跑探针：stdin 收样例，stdout 收「序号<TAB>哈希」
            ProcessBuilder pb = new ProcessBuilder("go", "run", "./main");
            pb.directory(tmp.toFile());
            pb.environment().put("GO_REAL_MODEL_DIR", modelDir.toAbsolutePath().toString());
            pb.environment().put("GOPROXY", "off");
            pb.environment().put("GOFLAGS", "-mod=mod");
            pb.redirectErrorStream(false);
            Process proc = pb.start();

            StringBuilder stdin = new StringBuilder();
            for (int i = 0; i < CASES.size(); i++) {
                Case c = CASES.get(i);
                stdin.append(c.userId).append('\t')
                        .append(c.targetType).append('\t')
                        .append(c.targetId).append('\t')
                        .append(c.authorId).append('\t')
                        .append(c.action).append('\t')
                        .append(c.occurredAt).append('\t')
                        .append(c.isFirst ? "1" : "0").append('\n');
            }
            proc.getOutputStream().write(stdin.toString().getBytes(StandardCharsets.UTF_8));
            proc.getOutputStream().close();

            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    err.append(line).append('\n');
                }
            }
            int exit = proc.waitFor();
            assertEquals(0, exit, "Go 侧探针退出码 " + exit + " ⇒ " + err);

            Map<String, String> result = new TreeMap<>();
            for (String line : out.toString().split("\n")) {
                if (line.isBlank()) {
                    continue;
                }
                int tab = line.indexOf('\t');
                assertTrue(tab > 0, "Go 侧输出格式异常：" + line);
                result.put(line.substring(0, tab), line.substring(tab + 1).trim());
            }
            return result;
        } finally {
            deleteRecursively(tmp);
        }
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static void deleteRecursively(Path root) {
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 临时目录清理失败不影响结论
                }
            });
        } catch (IOException ignored) {
            // 同上
        }
    }

    /** 一个对拍样例。 */
    private record Case(long userId, String targetType, String targetId, long authorId,
                        int action, long occurredAt, boolean isFirst) {
    }

    // ───────────────────────────── Go 侧探针源码 ─────────────────────────────

    /**
     * 垫片：只声明真源哈希函数<b>真正读到</b>的字段。
     *
     * <p>⚠️ 它是<b>被核对的对象</b>，不是信任对象 —— {@link #PROBE_GO} 会拿真源的
     * {@code like.go} 逐条比对本文件的字段名与类型，不一致就退出失败。
     */
    private static final String SHIM_GO = """
            package model

            // LikeProcessPayload —— 只保留 likeMessageHash 读到的字段。
            // ⚠️ 真源见 service/like/rpc/internal/model/like.go:37-43，由 probe.go 核对。
            type LikeProcessPayload struct {
            	Record     *LikeRecord
            	OccurredAt int64
            	IsFirst    bool
            }

            // LikeRecord —— 只保留 likeMessageHash 读到的字段。
            // ⚠️ 真源见 service/like/rpc/internal/model/like.go（type LikeRecord struct）。
            type LikeRecord struct {
            	UserID     int64
            	TargetType string
            	TargetID   string
            	AuthorID   int64
            	State      int32
            }
            """;

    /**
     * 探针：暴露真源哈希函数 + 用 {@code go/parser} 核对垫片与真源一致。
     *
     * <p>核对内容：真源 {@code LikeRecord} 的
     * {@code UserID/TargetType/TargetID/AuthorID/State} 五个字段、
     * 真源 {@code LikeProcessPayload} 的 {@code Record/OccurredAt/IsFirst} 三个字段
     * —— 字段名与类型必须<b>逐条相同</b>。
     */
    private static final String PROBE_GO = """
            package model

            import (
            	"fmt"
            	"go/ast"
            	"go/parser"
            	"go/token"
            	"os"
            	"path/filepath"
            	"reflect"
            	"strings"
            )

            // HashForProbe 调用真源里的 likeMessageHash（来自逐字节复制的 like_message_hash.go）。
            func HashForProbe(userID int64, targetType string, targetID string, authorID int64,
            	action int32, occurredAt int64, isFirst bool) string {
            	h, err := likeMessageHash(&LikeProcessPayload{
            		Record: &LikeRecord{
            			UserID:     userID,
            			TargetType: targetType,
            			TargetID:   targetID,
            			AuthorID:   authorID,
            			State:      action,
            		},
            		OccurredAt: occurredAt,
            		IsFirst:    isFirst,
            	})
            	if err != nil {
            		panic(err)
            	}
            	return h
            }

            // expectRecord 记录真源 LikeRecord 里哈希函数读到的字段及其类型。
            var expectRecord = map[string]string{
            	"UserID": "int64", "TargetType": "string", "TargetID": "string",
            	"AuthorID": "int64", "State": "int32",
            }

            // expectPayload 记录真源 LikeProcessPayload 里哈希函数读到的字段及其类型。
            var expectPayload = map[string]string{
            	"Record": "*LikeRecord", "OccurredAt": "int64", "IsFirst": "bool",
            }

            // VerifyShimAgainstRealSource 用 go/parser 解析真源 model 目录下的**全部**非测试 .go 文件，
            // 把真实结构体字段与垫片逐条比对。真源改了字段 ⇒ 这里失败退出，
            // 而不是安静地拿一份过期垫片去比。
            //
            // ⚠️ 为什么扫全目录而不是写死某个文件：真源里 LikeRecord 在 data_model.go、
            //   LikeProcessPayload 在 like.go ⇒ 写死单文件 = 探针自己先失效（实测踩到）。
            func VerifyShimAgainstRealSource(dir string) error {
            	real := map[string]map[string]string{}
            	entries, err := os.ReadDir(dir)
            	if err != nil {
            		return fmt.Errorf("read real model dir: %w", err)
            	}
            	fset := token.NewFileSet()
            	for _, e := range entries {
            		name := e.Name()
            		if e.IsDir() || !strings.HasSuffix(name, ".go") || strings.HasSuffix(name, "_test.go") {
            			continue
            		}
            		f, perr := parser.ParseFile(fset, filepath.Join(dir, name), nil, 0)
            		if perr != nil {
            			return fmt.Errorf("parse real %s: %w", name, perr)
            		}
            		for _, decl := range f.Decls {
            			gd, ok := decl.(*ast.GenDecl)
            			if !ok || gd.Tok != token.TYPE {
            				continue
            			}
            			for _, spec := range gd.Specs {
            				ts, ok := spec.(*ast.TypeSpec)
            				if !ok {
            					continue
            				}
            				st, ok := ts.Type.(*ast.StructType)
            				if !ok {
            					continue
            				}
            				fields := map[string]string{}
            				for _, field := range st.Fields.List {
            					if len(field.Names) == 0 {
            						continue
            					}
            					typ := exprString(field.Type)
            					for _, nm := range field.Names {
            						fields[nm.Name] = typ
            					}
            				}
            				real[ts.Name.Name] = fields
            			}
            		}
            	}
            	if err := compareStruct("LikeRecord", real["LikeRecord"], expectRecord, reflect.TypeOf(LikeRecord{})); err != nil {
            		return err
            	}
            	return compareStruct("LikeProcessPayload", real["LikeProcessPayload"], expectPayload, reflect.TypeOf(LikeProcessPayload{}))
            }

            func compareStruct(name string, real map[string]string, expect map[string]string, shim reflect.Type) error {
            	if real == nil {
            		return fmt.Errorf("真源里找不到结构体 %s", name)
            	}
            	for field, wantType := range expect {
            		gotType, ok := real[field]
            		if !ok {
            			return fmt.Errorf("真源 %s 缺字段 %s（垫片按它建过）", name, field)
            		}
            		if gotType != wantType {
            				return fmt.Errorf("真源 %s.%s 类型 = %s，垫片按 %s 建 ⇒ 垫片已过期", name, field, gotType, wantType)
            		}
            	}
            	// 垫片自身也要与期望集完全一致（多一个字段同样说明垫片与真源不同步）。
            	if shim.NumField() != len(expect) {
            		return fmt.Errorf("垫片 %s 有 %d 个字段，期望 %d 个", name, shim.NumField(), len(expect))
            	}
            	for i := 0; i < shim.NumField(); i++ {
            		f := shim.Field(i)
            		if _, ok := expect[f.Name]; !ok {
            				return fmt.Errorf("垫片 %s 多出字段 %s", name, f.Name)
            		}
            	}
            	return nil
            }

            func exprString(e ast.Expr) string {
            	switch t := e.(type) {
            	case *ast.Ident:
            		return t.Name
            	case *ast.StarExpr:
            		return "*" + exprString(t.X)
            	case *ast.SelectorExpr:
            		return exprString(t.X) + "." + t.Sel.Name
            	case *ast.BasicLit:
            		return t.Value
            	}
            	return fmt.Sprintf("%T", e)
            }

            // RealModelDirFromEnv 供 main 使用。
            func RealModelDirFromEnv() string {
            	return strings.TrimSpace(os.Getenv("GO_REAL_MODEL_DIR"))
            }
            """;

    /** 探针入口：从 stdin 读样例，stdout 输出「序号<TAB>哈希」。 */
    private static final String MAIN_GO = """
            package main

            import (
            	"bufio"
            	"fmt"
            	"os"
            	"strconv"
            	"strings"

            	"probe/model"
            )

            func main() {
            	dir := model.RealModelDirFromEnv()
            	if dir == "" {
            		fmt.Fprintln(os.Stderr, "缺少 GO_REAL_MODEL_DIR")
            		os.Exit(3)
            	}
            	if err := model.VerifyShimAgainstRealSource(dir); err != nil {
            		fmt.Fprintln(os.Stderr, "垫片与真源不一致：", err)
            		os.Exit(4)
            	}
            	sc := bufio.NewScanner(os.Stdin)
            	sc.Buffer(make([]byte, 1024*1024), 1024*1024)
            	idx := 0
            	for sc.Scan() {
            		line := sc.Text()
            		if strings.TrimSpace(line) == "" {
            			continue
            		}
            		f := strings.Split(line, "\\t")
            		if len(f) != 7 {
            			fmt.Fprintf(os.Stderr, "样例格式错误：%q\\n", line)
            		os.Exit(5)
            		}
            		userID, _ := strconv.ParseInt(f[0], 10, 64)
            		targetType := f[1]
            		targetID := f[2]
            		authorID, _ := strconv.ParseInt(f[3], 10, 64)
            		action, _ := strconv.ParseInt(f[4], 10, 32)
            		occurredAt, _ := strconv.ParseInt(f[5], 10, 64)
            		isFirst := f[6] == "1"
            		h := model.HashForProbe(userID, targetType, targetID, authorID, int32(action), occurredAt, isFirst)
            		fmt.Printf("%d\\t%s\\n", idx, h)
            		idx++
            	}
            	}
            """;
}
