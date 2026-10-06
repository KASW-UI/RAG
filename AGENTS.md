# AGENTS.md

This file contains the complete policy for `RAG`. It is the only file that
every agent loads automatically, so every rule lives here. Files under
`.agents` are task guides. They explain how to do a specific job.
They cannot add or weaken a rule in this file.

[PROJECT_DESCRIPTION].

[PRIMARY_REFERENCE_DESCRIPTION].
[填写：

* 项目是什么：
* 项目解决什么问题：
* 项目的主要技术栈：
* 如果存在参考实现 / 上游项目，写明其名称：
* 它定义什么：行为、协议、性能目标、兼容性、数据格式等：
* 如果不存在 primary reference，明确写 `No primary reference.`]

## Start here

1. Run `scripts/agent-start.py`. Pass `--intent operator|helper|read-only` and `--row <ID>`
   when you know them. Otherwise, relay its welcome and ask what
   work is intended. Follow the printed action, then run the command again.
2. Declare a role. Use `scripts/agent-role.py claim operator` for a multi-step
   integration campaign. Use `claim helper --row <ID>`
   for one scoped task. Use `claim read-only` for inspection.
   The `operator` claim records the current worktree as a coordinator.
   Another coordinator does not block the claim.
   Add `--headless` only when the developer explicitly says the run is
   unattended. Never infer this setting.
3. Run `scripts/now.py` to get the live position. Read
   `.agents/NOW.md` for the operator's current gate and next actions.
   The command output is derived. The file is authored and fits on one screen.
4. Read only the claimed work item, its spec, its evidence, and the task guide for
   the current job.
5. Run `scripts/agent-preflight.sh` before you edit a file.

Never infer a role, host, permission, or developer preference. Resolve
`.env` and `.agents/developer-preferences.md` from the shared checkout.
Ask only for the one value that the current gate needs. If a value is unavailable,
leave its gate `PENDING`. Never convert a missing value into an assumption.
Preferences control operations only. They cannot reduce a correctness, evidence,
attribution, or testing obligation.

**Create both files on first use.** Neither is tracked, so a fresh checkout has
neither, and `scripts/agent-start.py` reports the absence and routes you to ask.
Ask the developer for the one value the current gate needs. Record an
environment value with `scripts/agent-onboard.py --env-set KEY=VALUE`, which refuses any key
`.env.example` does not declare. Record a preference by copying
`.agents/developer-preferences.example.md` and editing the one entry. Leave
every key you did not ask about empty, because empty means unavailable and its
gate stays `PENDING`. A host name, a share path, or a checkout path written in
a repository document is another developer's resolved value. It is never a
default, and reading one instead of asking is the failure this rule names.

[如果项目不需要 `.env` 或 developer preferences，不要自行删掉这一套规则。
明确决定是否保留，并将上述变量替换成项目实际机制。]

## History is git

The project has no state log. Git is the history, and the history must agree
with the tree.

| Question                         | Command                                     |
|----------------------------------|---------------------------------------------|
| Did this work item already land? | `git log --oneline --grep '<WORK-ITEM-ID>'` |
| When did this symbol change?     | `git log -S'<symbol>' --oneline -- <path>`  |
| What happened to this file?      | `git log --follow --oneline -- <path>`      |
| What is on main that I lack?     | `git log --oneline HEAD..upstream/main`     |
| Why is this line like this?      | `git log -L '<start>,<end>:<path>'`         |
| What did that commit change?     | `git show --stat <sha>`                     |

The roadmap row states the current position of a work item. Git and the row's spec
state how it got there. Before you conclude anything about past work, read the
spec and run `git log -S`. Do not derive the history again.

## Every change starts from an issue

**Do not start work without an open issue.** Before you claim a work item or
write code, make sure that an issue tracks the work. Open one if none exists.
Link the issue in three places that must agree: the issue's own `Row:` line, the
work item's spec, and the pull request body.

**`GitHub` is the index.** There is no index file. `gh issue list`
is the intake surface, and the owning work item lives in the issue body as its
first line:

```text
Row: `<WORK-ITEM-ID>`
```

Write a dash when no work item owns it yet and a spec lists it under `## Owed`
instead. `scripts/agent-issue-index.py` renders the tracker into an untracked snapshot
so the record gates can run offline; nothing commits it, so no pull request
writes it.

[如果项目没有 GitHub Issues，必须在这里填写真实的 issue / ticket / task
系统，而不是让 Agent 自己猜测。]

A bug that you find during other work still needs an issue. Filing the issue does
not defer the fix. File it, fix it in the same flow, reference it in the
commit, and close it. The person who found the bug has the context to fix it.
Traceability is the goal, not another round trip.

**An issue you do not fix in the same flow has to say who owns it.** It names an
owning work item in its `Row:` line, or a spec lists it under `## Owed`.
`scripts/check-agent-record.py` gates that for every issue a change references.
Filing without fixing is therefore a gate failure rather than a habit. The gate
is scoped to what a change cites, because a count over every open issue moves
whenever anyone files one and could only ever fail `main` for reasons no commit
caused.

**An issue closes when the work lands.** The pull request that lands the fix
closes it, and its body carries the closing keyword that does so. Do not leave a
fixed issue open for a later sweep; the person who landed the fix is the last
one who knows it is fixed.

**An issue the tree falsifies closes with that evidence.** When the file,
checker or behaviour an issue describes no longer exists, close it and say what
falsified it. Do not re-spec it, and do not re-verify it a third time. Intake
without an exit is how stale work accumulates.

This in-flow rule applies to small and clear fixes. Use the normal work item,
spec, and fresh-review path for a surprising fix. Use that path when a fix needs
its own spec or changes checker semantics. The in-flow rule is not a bypass.

## Spec before code

A work item cannot become `READY` or `ACTIVE` without a committed
`.agents/specs/<slug>.md`. Commit the spec before implementation. Never write
the spec after the implementation. The spec contains scope, upstream anchors,
design, risks, tests, gates, evidence, and stop conditions.

At work-item claim, before you write the spec, ask the developer whether the spec
and implementation use one pull request or separate pull requests. Recommend one
pull request. Record the answer under `## Git integration` in
`.agents/developer-preferences.md`, and do not ask again for that work item. When no
answer is recorded and no split case applies, use one pull request. This default
is repository policy, not an inferred preference.

The single pull request is a recommendation, not an enforcement rule. Use
separate pull requests whenever the developer selects that shape. A split is
often useful for these cases:

* A helper dispatch needs a base-reachable committed spec before the helper can
  start.
* A large campaign benefits from agreement on the scope before implementation
  waves start.
* A change deliberately adds roadmap items, issues, or specs without product
  code.

Commit order still proves that the spec came first when one pull request carries
the spec and implementation. Do not add a checker that selects the pull request
shape.

Before you claim a work item, verify the gap against the current code, tests,
issues, pull requests, `NOW.md`, and the owning work item. If
the work already landed, already has an owner, or no longer matches its record,
reconcile the record first and do not implement.

When a work item reaches `DONE`, add an `## Outcome` section to its spec. Record
what you measured, what you rejected and why, and why each default has its value.
The code and Git history do not record these decisions.

[这一节原则上不要压缩。这里定义的是“为什么必须先做 spec、什么时候
可以进入 implementation、PR 如何拆、claim 前检查什么、DONE 后记录什么”。
如果你的项目有不同状态名，只替换状态名，不改变这套逻辑。]

## How we write

Use [the commit and pull request guide](`.agents/style/commit.md`) for commit
subjects, commit bodies, pull request titles, pull request descriptions, branch
names, changelog entries, and release-note lines. Use [the technical-English
guide](`.agents/style/prose.md`) for repository documents and all session prose.
Session prose includes progress updates, decisions, questions, and final
reports.

These guides control language and document structure. They cannot add unrelated
project policy or weaken this file. This file takes precedence when a guide
conflicts with a repository convention or rule.

Two of those rules are gated, and `scripts/check-commit-style.py` enforces them.
A commit subject does not end in a period. A commit has an authored body,
because the reader of a commit already has the diff and lacks the reason. One
sentence satisfies the body rule.

Subject length and body wrapping stay guidance in the guide, and no gate
enforces them. Measured over the last 200 non-merge commits, a 72-character
subject limit fails 164 commits and a 72-column body wrap fails 3658 lines. A
gate that fires on ordinary work is the defect, not the discipline.

The guides bind new prose. Rewriting an existing file to satisfy a style rule is
out of scope unless a work item asks for that rewrite.

## How work gets done

Work is delegated. Another person or agent reviews every implementation, and the
operator verifies the result. This sequence is the method, not a suggestion:

1. A **fresh implementer** works from the committed spec. The implementer ports
   or writes the smallest test that fails for the intended reason. They capture
   the red result, make the minimum complete change, and get focused green.
   Then they run the full gate.
2. A **fresh reviewer** reviews the immutable head. The reviewer cannot be the
   agent that wrote the code. They inspect the change statically and mutate each
   claimed guarantee in a scratch copy. This mutation proves that the tests
   detect the defect. Mutate the guarantee. Do not only read it. The reviewer
   restores the tree byte-for-byte after each mutation.
3. A **fresh implementer** repairs each finding. The coordinating session never
   repairs a finding. Repeat the focused gate, full gate, and fresh scoped
   review until the result is `PASS`. Attempt budgets control scheduling. They
   never stop a correctable finding. Only explicit developer direction or a
   precise external blocker stops the loop.
4. The **operator reruns the work item's gate itself**. An implementer or
   reviewer report is an input, never a gate result.

Every delegated task states the goal, context, exact scope, exclusions,
constraints, completion condition, required evidence, authority, output
contract, and stop conditions. Return `NEEDS_CONTEXT` when binding context is
missing. Do not guess. Return `NEEDS_DECISION` for a material disagreement
rather than changing the scope silently. Use the versioned contracts in
`.agents/prompts/`.

The operator is a **coordinator**. It holds the plan. It merges reviewed pull requests and dispatches agents into separate worktrees.
It does not write an implementation that needs independent review.

**More than one operator can run at the same time.**
`scripts/agent-role.py claim operator` records the coordinator and never refuses
because another operator exists. The `show` command lists the other operators.

**Never force-push `main`.** Nobody can use `--force` or `--force-with-lease` on
`main`. A plain `git push` rejects a non-fast-forward update. Git provides the
coordination lock. After a rejected push, fetch, merge again, rerun the gate,
and push again. Never force the update.

## Gates

Correctness comes first — every spec's acceptance criterion must pass before any performance or refactor work begins.

Use `./mvnw verify` as the denominator. Never use a debugging-only
configuration as the denominator unless the spec explicitly defines it
as the product target.

Record the test recipe, the commit SHA, and the diff against the spec.
Reproduce a green build on a clean clone before you accept it.

Never declare a ceiling. An apparent performance limit is an unresolved
implementation difference. Keep the gap open and name the next hypothesis.

Report exactly one result for each applicable rule. The result is
satisfied, narrowly waived, pending a named external authority or resource,
or failing. A permanent report-only state is not a result.

## Shared seams

A capability is not done until the shared surface can reach it.

* Route `[CAPABILITY_A]` through `[SHARED_SEAM_A]`.
* Route `[CAPABILITY_B]` through `[SHARED_SEAM_B]`.
* Route `[CAPABILITY_C]` through `[SHARED_SEAM_C]`.
* Expose every shipped capability through `[PUBLIC_ENTRY_POINT]`. Examples,
  servers, clients, or adapters are thin clients of this public interface.
  They never include internal implementation details unless explicitly required.

[这里必须填写你项目中真实存在的“共享接缝”：
不是按 Controller / Service / Repository 猜，而是填写真正决定
多个功能如何进入同一公共机制的接口、dispatcher、registry、runtime
entry point、middleware chain、shared abstraction 等。]

Extend a shared seam when it cannot represent the required behavior. Otherwise,
record one exact tracked exception. Never write a parallel path by hand.

Add new files for new [HARDWARE / ARCHITECTURES / FEATURES / SERVICES / MODULES].
Mirror the [PRIMARY_REFERENCE] file structure where such structure is part of
the compatibility contract.

[如果项目不存在 model / hardware / architecture porting，删除对应事实，
但不要删除“新增能力必须进入共享 seam，而不是偷偷建立 parallel path”
这一规则。]

[如果存在多个实现变体，例如 quantized arms、不同 backend、不同协议版本，
在这里明确列出哪些变体是“standing requirement”，哪些可以延期。]

## Nothing lands dead

A shared seam says where a capability routes. This section says whether anything
reaches it. The two failures are different.

What lands is reachable from a production entry point at its own merge commit:
`[PUBLIC_ENTRY_POINT_A]`, `[PUBLIC_ENTRY_POINT_B]`, the loader, registry,
server, command-line path, RPC endpoint, or other registered production path
on its default configuration. An example's internals are not one, and a test is
not one. The smallest failing test enters the new code through that entry point.
A unit test that constructs the type by hand proves that the class works, never
that anything reaches it.

A staged slice may land unreached only when the commit body and the pull request
body name what is unreached, the work-item ID that owns the wiring, and the issue
that tracks it, and the work item's spec lists it under `## Owed`. There is no
registry, and silence is not an exception.

The fresh reviewer mutates for this. Delete the production call site in a scratch
copy and rerun the focused gate. A gate that stays green without the call site
measures a class, not a capability.

Method, the shapes dead code takes, and why no checker enforces this:
`./agents/reachability.md`.

## Records

Every inventory item has a stable ID. It records the upstream source, local
anchor, tests and evidence, spec, lifecycle state, owner, and issue in the
correct matrix. When a lifecycle state changes, update the roadmap row and its
owning matrix row in the same change.

For a concurrent edit to a keyed record, take the complete target-branch
version. Apply the scoped edit again. Verify that unrelated keys remain
byte-for-byte equal. Never accept an automatic three-way merge of a keyed record.

**Do not create a surface that every pull request must write.** If N concurrent
pull requests edit file F, that file is a lock. A record surface can have only
one of these shapes:

* One file per row, read with a glob.
* A value that is derived at read time and is not stored.

Rewrite every other record surface into one of these shapes.

**An append-only file with `merge=union` is NOT one of them.** A merge mechanism
that appears to make a shared record safe but is not enforced by the forge is
not a concurrency solution. The driver can resolve the collision on one machine
while the forge still conflicts, so the file reads as safe locally and is a lock
in the only place that decides whether work can land.

[如果你的项目曾经有真实的 shared-record conflict，直接把原项目的事故、
commit 数量、PR 数量和 issue 编号填进这里；如果没有，不要伪造数字。
保留机制解释即可。]

A conflicted pull request is worse than a failing one. The forge may never
schedule CI for it, so it carries zero check-runs and reads as unverified rather
than red. Do not reach for `merge=union` to make a shared file safe. Derive it,
or give it one file per writer.

Two rules follow. **Limit an entry, not a shared file.** A shared-file budget
forces each addition to remove another entry. Merging two such edits cleanly is
worse than conflicting, because it applies both removals. **Never store a
measurement of one file inside another file.** A number that changes after each
edit couples every pull request to lines that it does not own.

A gate often creates the lock. If a checker requires every change to edit one
shared file, the checker is defective. Move the obligation to a per-row surface.
Do not delete the obligation.

Move superseded detail into `.agents/completed/`. Keep its links and
provenance. Never delete evidence to reduce context.

## Public documents

Each public document has one purpose and one trigger. These documents are
projections, not narratives. Each fact lives in one document.

| Surface                             | Changes when                                                    |
| ----------------------------------- | --------------------------------------------------------------- |
| `docs/FEATURES.md`         | a feature, model, backend, or capability surface changes        |
| `docs/USAGE.md`           | a command, API, config key, install step, or workflow changes   |
| `README.md`                         | a user-visible headline, positioning, or quick start changes    |
| the moved work-item spec's `## Now` | a work item changes lifecycle state                             |

Editing implementation, interface, or test files on its own owes none of these.
A lifecycle change owes only the moved work-item spec's `## Now`; Git and the
work item's records carry detailed status and history. Publishing a benchmark
owns one detail file and its index row. `.agents/NOW.md` is authored
only at operator cadence and is never a per-work-item lifecycle write.

## Work happens in a worktree

**Do every unit of work in its own linked worktree and task branch.** A unit of
work is one issue's change together with the records that change invalidates. A
feature, a fix, a policy change, a document, and a one-line gate repair are each
a unit. A claimed work item uses `row/<ID>`. Pin the base SHA when you
create the worktree.

**A record edit rides in the pull request whose change made the record stale.**
It is not a unit of work by itself. A record-only pull request is still correct
when the record is the work: a stale row, a corrected pin, a newly filed gap. It
is wrong when it only restates what just landed, because that costs a branch, a
gate run, and a fresh review to say something the landing change already knew.
This narrows what counts as a unit. It never licenses bundling unrelated work
into one branch.

Keep the shared checkout on a clean `main`. **Never use it as a work surface.**
Other worktrees branch from it, so it must remain current and safe. Never edit,
commit, or stash in the shared checkout.

Remove the worktree and delete its branch when the work merges, closes, or is
abandoned. A worktree is a complete checkout. Stale worktrees fill the disk and
can make gates fail when temporary space runs out.

## Landing work

Move work to `main` from its task branch, never from the shared checkout. A
helper opens a reviewed `row/<ID>` pull request. An operator with recorded
merge authority can merge the branch locally with a commit that names it. This
local merge keeps an in-flow repair to one step. The task branch still makes the
change visible, reversible, and attributable.

Run the applicable gate before every push. Chain the successful gate directly
to the exact-SHA push. **Never force-push `main`, and never add a force option to
a script that can target it.** The scope is `main`, exactly as in §"How work gets
done" -- a task branch you own is yours to rebase and force-push when the project
allows it. A rejected push on `main` protects another merge. Fetch, merge again,
rerun the gate, and push again. Hooks are bypassable convenience, not evidence.
If you cannot query the remote, report `REMOTE_UNVERIFIED`. Unknown is not
absence or success, and it does not authorize cleanup.

Merge each verified pull request in the current session. Close an obsolete pull
request and record the reason. Never end a session with a verified, unmerged
pull request.

**The pull request body is the landed commit message.** The repository sets
`squash_merge_commit_message = PR_BODY`, so write the body to the same standard as a
commit message and end it with the trailer block below. This setting is part of
the contract, not a convenience.

[如果你的 forge / GitHub 设置不是 PR body → squash commit message，
这里必须填写真实配置，不要让 Agent 猜。]

No gate can hold that setting if reading it needs a network call and no checker
here may make one. If the landed commits start failing the trailer gate again,
read the setting first. `tests/scripts/test_check_commit_trailers.py` pins the supported squash shapes, so
the difference between them is executable.

**Read the body before you merge it.** The CI job that holds a body to this
contract runs in a queue and can be outrun. Run the same check yourself, on the
bytes that are about to become the commit:

```sh
python3 scripts/agent-pr-body.py --pr <N>
```

It exits 0 when the body will land clean, 1 when the body was read and fails the
contract, and 3 when it could not be read at all. A 3 is `REMOTE_UNVERIFIED`
and is never a pass. The command is a belt to the CI guard's braces and not a
replacement for it: the forge reads the body again from its own event payload,
which is what catches an edit made after you looked.

Every commit contains a bare `FOLLOWING_AGENTS_PROTOCOL` paragraph and these
trailers:

```text
Following-Agents-Protocol: true
AI-Assisted: true
Assisted-by: AGENT:MODEL [TOOL]
```

AI tools never add `Signed-off-by` or `Co-Authored-By`. The human submitter owns
and reviews the change.

This prohibition stops an AI from claiming authorship. It does not stop the
forge from recording the submitter. The forge may add a `Co-authored-by` trailer
for the account that opens a pull request. The accepted form uses the forge's
normal noreply address. This trailer records the submitter, even when the
account is a bot. `AI-Assisted` and `Assisted-by` record AI involvement and are
always required. `Signed-off-by` has no exception because it is a legal
assertion, not attribution.

Classify policy, checker, document, script, test, continuous integration (CI),
generated, and product paths explicitly. Never hide mutable files behind a
general directory exemption. The project has no line budget unless a real
policy explicitly defines one. Size is a review decision. Split a change when
parts help the reviewer, not when a counter requires it.

## Changing the rules or a checker

A checker's message defines what it enforces. This file states the rule in
prose. No gate checks whether the two descriptions agree, and that is deliberate.
Keeping two descriptions in sync is the failure mode that this protocol was
built to remove.

A semantic checker change needs a spec, a red-before test or mutation, and
green-after evidence. Never make a red gate green by deleting an assertion or
widening its scope.

The project has no waiver registry unless explicitly defined below. An exception
registry is a state log, and this protocol has no state log unless one is
explicitly created. A commit that needs an exception argues for it in its own
message. The reason then stays with the diff, author, and date. It cannot drift
from the tree because it is part of Git history. Use `git log --grep` to find it.
An exception is visible debt, not success. A reviewer who rejects the reason does
not merge the change.

## Task guides

Read the guide for the current job.

| Doing this                                           | Read                    |
| ---------------------------------------------------- | ----------------------- |
| [TASK TYPE A]                                        | `[TASK_GUIDE_A]`        |
| [TASK TYPE B]                                        | `[TASK_GUIDE_B]`        |
| Running gates, proving correctness, reviewing        | `.agents/verification.md`  |
| Proving a change is reached                          | `.agents/reachability.md`  |
| Fixing a bug                                         | `.agents/bugfixing.md`        |

Only include task guides that actually exist. The important rule is that
`AGENTS.md` defines the authority and `.agents/` guides explain execution of a
specific job; guides cannot weaken this file.

## Commands

```sh
scripts/agent-start.py                          # always first
python3 scripts/agent-role.py show
scripts/agent-preflight.sh                      # before edits
scripts/agent-preflight.sh --staged             # before commit
python3 scripts/agent-ready.py                  # before remote handoff
python3 scripts/agent-pr-body.py --pr <N>      # before merging: the body IS the message
python3 scripts/agent-integration.py --base upstream/main
```

Never push, merge, manage services, use external compute, or download large
assets without authority recorded in developer preferences or given for the
current task.

## Authority and permissions

[这一节建议保留，即使你的原始 AGENTS 没有把它单独作为章节。
如果权限规则散落在其他地方，Agent 很容易把“技术上能做”误认为
“被授权做”。]

The agent may:

* inspect repository files;
* inspect Git history;
* run read-only commands;
* run the gates required by the current task;
* modify files inside the declared worktree and declared scope;
* create the task branch and worktree when the workflow permits it;
* prepare commits and pull requests when explicitly authorized.

The agent may not:

* infer missing permissions;
* infer production credentials;
* modify protected branches outside the declared merge procedure;
* change external systems without recorded authority;
* modify frozen project constraints without human confirmation;
* silently expand the task scope;
* replace a missing decision with an implementation guess.

If an operation changes an external system, shared resource, security boundary,
public contract, data schema, deployment behavior, or other irreversible or
high-impact state, require explicit authority unless the project policy already
grants that operation for the current role.

## Frozen project constraints

The following are project constraints that an agent must treat as fixed unless
the developer explicitly authorizes a change:

### External contracts

[填写：

* public API
* RPC / gRPC / HTTP contract
* protobuf / OpenAPI
* event schema
* error codes
* externally consumed JSON fields
* CLI contract
* compatibility guarantees
  ]

### Data and persistence contracts

[填写：

* database schema
* migration rules
* persistent data format
* cache key format
* serialization format
* object storage layout
* backward / forward compatibility requirements
  ]

### Security contracts

[填写：

* authentication mechanism
* authorization boundaries
* token / credential semantics
* tenant isolation
* permission model
* security-sensitive middleware
* audit requirements
  ]

### Runtime and infrastructure constraints

[填写：

* Java / Go / Python version
* JDK / compiler version
* OS
* container image
* deployment platform
* Kubernetes requirements
* required ports
* required environment variables
* external services
* resource limits
  ]

### Framework / library constraints

[填写：

* libraries that must remain
* libraries that cannot be introduced
* fixed framework versions
* generated-code rules
* build-system rules
  ]

### Repository structure constraints

[填写：

* fixed Java tree
* fixed package structure
* one-Go-file-to-one-Java-file rules
* generated directories
* forbidden directories
* naming conventions
* files that must exist
  ]

### Compatibility constraints

[填写：

* versions that must remain compatible
* old clients that must continue working
* protocol versions
* database migration compatibility
* cross-language compatibility
  ]

### Other constraints

[填写任何不能由 Agent 自己改变的事实。]

The agent must not reinterpret a frozen constraint as a suggestion.
If implementation appears impossible under a frozen constraint, return
`NEEDS_DECISION`. Do not silently change the constraint.

## Human decisions

The following decisions must be made by the human when they materially affect
system semantics:

* whether an external behavior or contract changes;
* whether a frozen constraint changes;
* whether a new dependency is acceptable when dependencies are constrained;
* whether a new persistence model or schema is acceptable;
* whether authentication or authorization semantics change;
* whether transaction boundaries change;
* whether concurrency semantics change;
* whether retry / idempotency semantics change;
* whether a new cache consistency model is acceptable;
* whether an API / RPC / event contract changes;
* whether compatibility may be broken;
* whether a new external service becomes part of the runtime;
* whether an implementation requires a business-rule decision not represented in
  the existing code or spec.

The agent may choose implementation details that preserve these decisions.
It must not make one of these decisions implicitly by choosing a convenient API,
framework pattern, directory structure, or default value.

When two technically valid implementations have materially different system
semantics, return `NEEDS_DECISION` rather than selecting one silently.

## Agent implementation boundary

The agent owns **how** the declared behavior is implemented.

The agent may decide:

* Java / Go / Python syntax;
* framework API usage;
* class / method implementation details inside the declared structure;
* boilerplate;
* repetitive mappings;
* dependency injection wiring when the dependency graph is already decided;
* test implementation;
* generated-code invocation;
* mechanical refactoring that preserves semantics;
* implementation details required to satisfy an already-defined design.

The human owns **what and why** when the choice changes system semantics.

The agent must preserve:

* external behavior;
* business rules;
* lifecycle semantics;
* state transitions;
* API contracts;
* error semantics;
* authentication;
* authorization;
* transaction boundaries;
* concurrency guarantees;
* retry and idempotency behavior;
* cache semantics;
* persistence semantics;
* event ordering;
* compatibility requirements;
* frozen project constraints.

If the agent discovers that the existing implementation and the requested change
do not determine one unique semantic behavior, it must not invent one.

Return:

```text
NEEDS_DECISION
```

and state:

1. what semantic decision is missing;
2. the concrete code paths affected;
3. the alternatives already supported by the existing system;
4. what would change under each alternative;
5. the smallest decision required from the developer.

## Stop conditions

Stop and return `NEEDS_CONTEXT` when:

* required repository context is missing;
* the required spec is missing;
* the current work item's owner or scope is unclear;
* a required external reference cannot be located;
* required evidence is unavailable;
* a required environment value is unavailable;
* the current gate cannot determine whether the operation is valid.

Stop and return `NEEDS_DECISION` when:

* the implementation requires a business decision;
* two valid interpretations change system semantics;
* a frozen constraint must change;
* an API, schema, security boundary, transaction boundary, concurrency model,
  retry policy, cache model, or compatibility contract would change;
* the requested scope conflicts with an existing project rule;
* the existing code does not contain enough information to determine the intended
  behavior.

Stop when a required external authority or resource is unavailable and the
operation cannot be performed safely.

Never turn:

* missing context into an assumption;
* missing permission into permission;
* missing evidence into success;
* `UNKNOWN` into `PASS`;
* `PENDING` into `DONE`;
* an implementation choice into a business decision.

Do not continue merely because a task is small. The size of a change does not
reduce the obligation to preserve semantics.

## Completion

A task is complete only when:

1. the declared scope is implemented;
2. the implementation preserves the frozen contracts;
3. the required tests exist and pass;
4. the focused gate passes;
5. the full applicable gate passes;
6. the fresh review has passed;
7. all review findings are resolved or explicitly recorded;
8. the required evidence is recorded;
9. the work item and issue agree with the tree;
10. required documentation projections are updated;
11. the exact commit / pull request state satisfies repository policy;
12. the operator has independently rerun the applicable gate.

A report saying that another agent passed a gate is evidence, not the gate result.
The operator must run the gate itself.

A task is not complete merely because:

* the code compiles;
* a unit test passes;
* the agent believes the behavior is correct;
* the reviewer says it looks correct;
* the pull request is open;
* the implementation exists in a worktree.

The completion condition is the complete chain from spec → implementation →
verification → review → gate → landing.

## Final report

The final report must state:

* what changed;
* why it changed;
* the work-item / issue ID;
* the files changed;
* the semantic decisions made;
* the assumptions that were explicitly allowed;
* the evidence produced;
* the tests and gates run;
* the exact result of each applicable gate;
* any remaining `PENDING`, `UNKNOWN`, `VOID`, or `NEEDS_DECISION` item;
* any external dependency or resource that prevented verification;
* the commit SHA;
* the pull request;
* whether the work was independently reviewed;
* whether the operator independently reran the final gate.

Do not report a task as complete when a required gate is unknown.

Do not hide unresolved work in prose such as "looks good", "should work",
"probably fine", or "no known issues".

If the work is blocked, say exactly what is blocking it and return the appropriate
state rather than inventing completion.

The final report is a record of what was actually established, not a summary of
what the agent intended to establish.
