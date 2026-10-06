# [TITLE] — [subtitle]

Row: `[ROW-ID]`.
Status: **[STATUS]**.
[Predecessor: `[PREDECESSOR-ROW-ID]`, in [`[predecessor-spec].md`]([predecessor-spec].md).]

[用 1–3 段说明这个 spec 为什么存在、它解决什么问题、它来自什么 issue / developer direction / 已确认的问题。这里记录“为什么要做”，不要在这里开始写实现细节。]

[如果有明确的 issue：]
User-directed [YYYY-MM-DD], issue
[#ISSUE](https://github.com/[ORG]/[REPO]/issues/[ISSUE]). Row `[ROW-ID]`
([row ownership / category / scope]).

[如果有直接的原始要求、设计约束或决定来源：]
The [developer / maintainer / issue / prior decision] says:
*" [原始要求 / 决策依据] "*.

## Scope

`[要修改的文件 / 模块 / 机制]` [需要发生什么变化，以及最终应表现成什么样。]

[继续描述这项工作的边界。明确哪些行为、文件、组件属于 scope。]

Out of scope: [明确列出不属于本 spec 的内容。]

[如果存在已有 gate / security boundary / compatibility constraint / landing
rule 等不可弱化的约束，在这里明确说明。]

## Design

**[Design area / representation / mechanism].** [具体设计。]

[描述数据结构、接口、状态、调用关系、文件布局、控制流或其他设计
决定。]

[如果设计涉及并发 / 原子性 / 生命周期 / ownership / transaction /
security boundary 等机制，在这里说明它为什么能够满足要求。]

[如果存在多个实现选择，记录选择后的方案，以及为什么它满足 scope
和既有约束。]

**[第二个 design area].** [具体设计。]

[继续描述。]

[如果存在迁移 / compatibility：]

**Migration.** [旧行为如何继续工作、旧数据/旧配置/旧调用方如何处理，
以及迁移何时结束。]

[如果有兼容旧格式、旧接口、旧状态的要求，在这里明确。]

[如果有 front door / caller / integration surface：]

**[Front door / integration].** [哪些入口需要改变，为什么，以及如何
保证新的行为真正从入口进入。]

## Gates

* `[focused test / gate]` (focused, RED first)
* `[relevant integration / full suite]`
* `[preflight / CI / build gate]`
* `[compatibility / contract / security gate, if applicable]`
* Mutation: [哪些关键 guard / branch / call / wiring 必须被删除或反转，
  focused suite 应当因此 RED；然后恢复并证明 GREEN。]

[如果某项 gate 不适用，不要伪造：]

* `[gate]`: not applicable because [reason].

[记录任何性能、资源、分布式、概率性或其他特殊 gate 时，说明它测量
的是什么，而不是只列命令。]

## Dependencies

* `[dependency]` — [为什么依赖它]
* `[dependency]` — [为什么依赖它]

[如果没有：]

None. `[language / framework / docs / tests / other]` only; no
`[GPU / network / external service / build dependency / etc.]`.

## Work breakdown

[用一个可以独立检查完成状态的单元描述工作，不要只是罗列文件。]

[Unit 1] — [要完成的整体行为，以及为什么这些修改必须一起完成。]

[Unit 2, if needed] — [行为 / mechanism / integration，以及为什么可以
作为独立单元。]

[如果拆分会导致中间状态违反 contract / gate / security / runtime
behavior，在这里明确说明。]

[如果这是 single unit：]

Single unit — [列出必须一起修改的 tool / implementation / tests /
entry points / documents，以及为什么拆开会留下不一致状态。]

## Risks/decisions

* **Risk: [风险].** [接受 / 缓解 / 拒绝该风险的决定，以及理由。]

* **Risk: [风险].** [决定，以及理由。]

* **Decision: [设计决定].** [为什么选择这个值 / 行为 / 默认值。]

* **Decision: [设计决定].** [为什么拒绝其他方案。]

[这里记录已经做出的判断，而不是把所有可能方案重新讨论一遍。]

[如果某个方案被明确拒绝：]

* **Rejected: [方案].** [为什么拒绝，以及什么证据 / 约束导致它被拒绝。]

[如果某个问题暂时无法关闭：]

* **Recorded, not closed: [问题].** [当前证据、为什么暂时不关闭、
  后续需要什么才能关闭。]

## Outcome

[只有在 Status 达到 DONE / LANDED 等最终状态时填写。]

Landed `[YYYY-MM-DD]` on `[branch / row / commit]`.

[用一段话说明最终实现了什么，以及最终行为是什么。]

[记录最终验证结果。]

[记录最终选择了什么、拒绝了什么，以及为什么。]

[如果发生过多轮 review：]

[Number] review rounds.

[Round 1] [发现了什么、如何修正、什么证据证明修正成立。]

[Round 2] [发现了什么、如何修正、什么证据证明修正成立。]

[如果最终仍有明确未解决但被接受的问题：]

Still declined / recorded, unchanged: [问题]. [为什么保持这个决定，
以及后续 remedy 是什么。]

[如果某个结论来自 measurement：]

Measured `[YYYY-MM-DD]` under `[environment / conditions]`:

| **Condition** | **Result** |
| ------------- | ---------- |
| `[condition]` | `[result]` |
| `[condition]` | `[result]` |

[说明 measurement 改变了什么决定。]

## Follow-up: issue [#ISSUE]

[只有存在独立 follow-up issue 时增加。]

[说明 follow-up 为什么从本 spec 中产生、它解决什么尚未完成的问题，
以及它是否改变本 spec 已经 landed 的结论。]

[如果没有 follow-up，不创建这个 section。]
