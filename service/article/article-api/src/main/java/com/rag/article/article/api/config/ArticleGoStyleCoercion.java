package com.rag.article.article.api.config;

import com.rag.users.common.http.GoStyleCoercion;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/**
 * article-api 专属的 Jackson 3 强转规则 = {@link GoStyleCoercion} 共享规则
 * + <b>一条 article 域补强</b>。
 *
 * <h2>为什么必须叠加、而不是直接用共享规则</h2>
 *
 * <p>{@code GoStyleCoercion}（users-common）刻意保留了一条豁免：
 * <b>「String → 整数字段保持接受」</b>。理由是 users 域<b>按真源逐字段实测</b>：
 * users 的 uid 字段带 {@code json:"uid,string"}，而 go-zero 的
 * {@code ,string} 语义是<b>「入参方向宽松（字符串与数字都收），出参方向严格」</b>
 * （AGENTS.md §DRIFT-MSGAPI-001 的根因分析：{@code json.Number} 的
 * {@code reflect.Kind()} 是 {@code string}，不是 {@code number}）。
 *
 * <p><b>article 域没有这个前提。</b>逐行核对 Go {@code service/article/api/internal/types/types.go}
 * （全文 86 行）—— <b>没有一个 {@code ,string} option</b>：
 * <pre>
 *   CreateArticleReq: title / brief,optional / content / cover_image_url,optional
 *                     / manual_type_tag,optional / secondary_tags,optional
 *   UpdateArticleReq: ... status,optional                ← status 是 int32，无 ,string
 *   ListArticlesReq:  page,default=1 / page_size,default=20 / desc,default=true
 * </pre>
 * ⇒ {@code {"status":"2"}} 在 Go 侧<b>必然</b>走
 * {@code processFieldPrimitive} 的 {@code typeKind != valueKind} 分支
 * ⇒ {@code newTypeMismatchError} ⇒ HTTP 400 text/plain。
 *
 * <p>若不叠加这条规则，Spring 的默认绑定会<b>接受</b> {@code {"status":"2"}}，
 * 把它变成 {@code status=2} 并真的下发到 article-rpc ——
 * 一个 Go 侧拒绝、RAG 侧接受的请求，是最典型的 L2 语义漂移。
 *
 * <h2>为什么<b>不</b>改共享的 {@code GoStyleCoercion}</h2>
 * <p>那条豁免对 users 域成立且已被验证。改共享层会连带把
 * user-api / admin-api 的已验证行为退回去。⇒ <b>补强要局部叠加，不要污染共享层</b>
 * （与 message 域 {@code MessageGoStyleCoercion} 同一条纪律）。
 *
 * <h2>⚠️ 只加一条，不多加</h2>
 * <p>Boolean/String 的补强<b>不需要</b>：本模块 JSON 入参里<b>没有任何 boolean 字段</b>
 * （{@code incr_view} / {@code desc} 都走 query string，由
 * {@link com.rag.article.article.api.form.GoFormValues} 按 Go 的
 * {@code convertTypeFromString} 处理，与 Jackson 无关）。
 * 为不存在的字段类型加规则，等于给后人留一个「这里还管别的」的错觉。
 */
final class ArticleGoStyleCoercion {

    private ArticleGoStyleCoercion() {
    }

    static void applyTo(JsonMapper.Builder builder) {
        GoStyleCoercion.applyTo(builder);

        // article 补强：无 ,string 的整数字段**拒绝** JSON 字符串
        //   LogicalType.Integer 覆盖 int / long / Integer / Long
        //   LogicalType.Float    覆盖 float / double / Float / Double
        // ⚠️ CoercionInputShape.String 是「字符串进数值」的形状名，不是「输出成字符串」；
        //    后者由 ,string tag 决定，在 Java 侧体现为「字段声明成 String」。
        builder.withCoercionConfig(LogicalType.Integer, cfg -> cfg
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail));
        builder.withCoercionConfig(LogicalType.Float, cfg -> cfg
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail));
    }
}