package com.rag.users.common.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 🟢 **跨 lane 依赖守门** —— 把 **G1 的销号结论**所依赖的两个不变量**钉成机器网**。
 *
 * <p><b>背景</b>：G1 销掉了 {@code DRIFT-USERSCOMMON-001}（Go 侧 1017/1018 文案），
 * 结论 = **两侧均不可观察 ⇒ 该条不成立**（证伪关闭 · 无代码修复）。
 * ⇒ ⇒ 但那个结论**依赖本模块（{@code service/users/users-common}）的两个类** ——
 * G1 不写本模块（写权归共享层波），所以只能登记 + 知会：
 * <ul>
 *   <li>{@link GoStyleRequest#normalize(Object, Supplier)} —— 内部调
 *       {@code target.assertGoRequiredFields()} ⇒ **必填校验在 service 层之前拦**；</li>
 *   <li>{@link GoStyleRequired#field(String, Object)} —— {@code value == null} 即抛
 *       {@link GoRequiredFieldException} ⇒ 缺 uid ⇒ **400 {@code field "uid" is not set}**。</li>
 * </ul>
 * ⇒ ⇒ **G1 写进它台账的复开触发条件** = 「必填校验不再在 service 层之前拦」
 * 或「{@code normalize} 不再调 {@code assertGoRequiredFields()}」。
 *
 * <p><b>本类就是那个触发器</b>：这两条一旦被改 ⇒ 本类红 ⇒ 失败消息直接指向
 * G1 的销号条目 ⇒ ⇒ **不靠「谁记得去复核」**，而是**机器逼着复核**。
 *
 * <h2>Go 出处（逐条可核）</h2>
 * <ul>
 *   <li>{@code field %q is not set} = go-zero {@code v1.10.2}
 *       （<b>Go 仓 {@code go.mod} 钉的版本</b>）{@code core/mapping/unmarshaler.go:1199}
 *       ⇒ {@code newInitError(name)}（同文件 {@code :1198}）⇒ **「字段缺席」的错误文本**；</li>
 *   <li>{@code field %q mustn't be nil} = 同文件 {@code :862}
 *       （{@code processNamedFieldWithValue} 内，函数头 {@code :854}）
 *       ⇒ **「显式 null」的错误文本**（与上一条**不同**，不能压成同一个）；</li>
 *   <li>「**零值 ≠ 缺席**」：Go 的零值 struct 里 {@code uid = 0} 是**合法值**
 *       ⇒ 只有「字段缺席 / 显式 null」才报错 ⇒ ⇒ 故本类第 ④ 条断言
 *       {@code field("uid", 0L)} **不抛**。</li>
 * </ul>
 */
class GoStyleRequiredFieldGateTest {

    @Test
    @DisplayName("① normalize 必须调 assertGoRequiredFields() ⇒ 必填校验在 service 层之前拦")
    void normalizeMustRunRequiredFieldAssertion() {
        // 🔴 直接证明「回调**被调用过**」⇒ 不依赖异常实例的同一性
        //    （DTO 内部自建异常实例 ⇒ 断言 isSameAs(别处 new 的实例) 是错的期望值）
        AtomicBoolean called = new AtomicBoolean(false);

        GoStyleRequiredFields dto = () -> {
            called.set(true);
            GoStyleRequired.field("uid", null);
        };

        assertThatThrownBy(() -> GoStyleRequest.normalize(dto, () -> {
            throw new AssertionError("req 非 null ⇒ factory 不该被调用");
        }))
                .as("normalize 必须原样抛出 DTO 必填校验抛出的异常"
                        + " ⇒ 这条红了 = 「必填校验不再在 service 层之前拦」"
                        + " ⇒ G1 的 DRIFT-USERSCOMMON-001 销号结论**必须复核**")
                .isInstanceOf(GoRequiredFieldException.class)
                .hasMessage("field \"uid\" is not set");

        assertThat(called)
                .as("normalize **必须**调用 assertGoRequiredFields()"
                        + " ⇒ 没被调用 = 必填校验被绕过 ⇒ service 层之前不再拦")
                .isTrue();
    }

    @Test
    @DisplayName("② normalize(req=null) ⇒ 走 factory 的零值 DTO，且仍跑必填校验")
    void normalizeWithNullRequestUsesZeroValueDtoAndStillValidates() {
        // 空 body ⇒ 用 factory 的零值 DTO（Go 的零值 struct）
        Supplier<GoStyleRequiredFields> factory = MissingUidDto::new;

        assertThatThrownBy(() -> GoStyleRequest.normalize(null, factory))
                .as("零值 DTO 也必须过必填校验（不能因为「零值」就跳过）")
                .isInstanceOf(GoRequiredFieldException.class)
                .hasMessage("field \"uid\" is not set");
    }

    @Test
    @DisplayName("③ field(name, null) ⇒ 400 文案逐字节 = field \"uid\" is not set")
    void missingFieldMessageMatchesGoByteForByte() {
        assertThatThrownBy(() -> GoStyleRequired.field("uid", null))
                .as("缺席 ⇒ Go 的 newInitError（unmarshaler.go:1199）⇒ 文案必须逐字节一致")
                .isInstanceOf(GoRequiredFieldException.class)
                .hasMessage("field \"uid\" is not set");
    }

    @Test
    @DisplayName("④ field(name, 零值) 不抛 —— 零值合法，只有「缺席 / 显式 null」才报")
    void zeroValueIsNotMissing() {
        // 🔴 正向对照：防「一律抛」的假网（Go 的零值 uid=0 是合法值）
        assertThatCode(() -> GoStyleRequired.field("uid", 0L))
                .as("零值 0L 不是缺席 ⇒ 不得报错")
                .doesNotThrowAnyException();
        assertThatCode(() -> GoStyleRequired.field("uid", ""))
                .as("空串也不是缺席 ⇒ 不得报错")
                .doesNotThrowAnyException();
        assertThatCode(() -> GoStyleRequired.field("uid", "x"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("⑤ 显式 null 走 mustn't be nil —— 与「缺席」是两种文案")
    void explicitNullHasItsOwnMessage() {
        assertThatThrownBy(() -> GoStyleRequired.explicitNull("uid", true, null))
                .as("显式 null ⇒ Go 的 processNamedFieldWithValue（unmarshaler.go:862）"
                        + " ⇒ 文案是 mustn't be nil，**不是** is not set")
                .isInstanceOf(GoRequiredFieldException.class)
                .hasMessage("field \"uid\" mustn't be nil")
                .satisfies(e -> assertThat(((GoRequiredFieldException) e).isExplicitNull())
                        .as("形态标记必须为「显式 null」")
                        .isTrue());
    }

    @Test
    @DisplayName("⑥ normalize 的零值 DTO 路径：uid=0 ⇒ 不抛（Go 的零值语义）")
    void zeroValueDtoPassesValidation() {
        assertThatCode(() -> GoStyleRequest.normalize(new ZeroValueDto(), ZeroValueDto::new))
                .as("uid=0 的零值 DTO 必须过校验 ⇒ 与 Go 的零值 struct 同语义")
                .doesNotThrowAnyException();
    }

    /** DTO 替身：必填校验里 uid 缺失 ⇒ 走 {@link GoStyleRequired#field(String, Object)}。 */
    private static final class MissingUidDto implements GoStyleRequiredFields {
        @Override
        public void assertGoRequiredFields() {
            GoStyleRequired.field("uid", null);
        }
    }

    /** DTO 替身：uid 是零值（{@code 0L}）⇒ 合法 ⇒ 不报错。 */
    private static final class ZeroValueDto implements GoStyleRequiredFields {
        @Override
        public void assertGoRequiredFields() {
            GoStyleRequired.field("uid", 0L);
        }
    }
}
