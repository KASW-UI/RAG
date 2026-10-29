package com.rag.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
/**
 * 共享层的响应信封 —— 对应 Go 侧 {@code service/common/response/response.go}。
 *
 * <p><b>Go 出处</b>：{@code service/common/response/response.go:3-7}
 * （{@code type Response struct { Code int `json:"code"`; Msg string `json:"msg"`; Data interface{} `json:"data"` }}`）
 * ⇒ <b>三个 JSON 键与字段顺序逐条一致</b>（Go 的 struct tag 顺序 = Jackson 的字段声明顺序）
 * ⇒ ⇒ <b>语义等价</b>（这是共享层里「逐条等价」的那一个包，见
 * {@code docs/DRIFT/common/alignment-2026-10-23.md} §六「response —— ✅ SEMANTICALLY EQUIVALENT」）。
 */
public class Response {
    private int code;
    private String msg;
    private Object data;
}