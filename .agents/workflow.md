```
issue（GitHub 入口）
├── body 写 "Row: \`ROW-ID\`" + Acceptance + Out of scope
├── gh issue list 索引
└── 链向 PR (Closes #N)
↓
spec.md (.agents/specs/<slug>.md)
├── Scope / Design / Upstream chain / Risks / Tests / Gates / Outcome
├── commit spec BEFORE implementation
└── 13 节完整模板
↓
AGENT 4 步循环                       
├── 1. fresh implementer
│     - IMP-TEST-FIRST（写最小失败测试、跑红、确认失败）
│     - IMP-VERIFY（跑每个 gate、exit 0 或 proven baseline）
│     - IMP-MUTATE（delete/invert 测试 pin 的行为，验证 test fail，再恢复）
│     - 跑 full gate
│
├── 2. fresh reviewer （**不能是 implementer 自己**）
│     - REV-STATIC（独立静态审）
│     - REV-MUTATION（scratch copy 里 mutate、验证 catch缺陷）
│     - REV-FULL-GATE（跑 full gate 一次）
│     - 输出 findings（severity 降序）→ PASS / FAIL
│
├── 3. fresh implementer 修 findings
│     - implementer 修 → reviewer 重新 → 重审│     - 重复到 PASS（attempt budget 不停 correctable finding）
│
└── 4. operator 自跑 gate
      - OP-VERIFY（implementer / reviewer 报告是 input，**不是 gate**）
      - operator 必须自己跑一次↓
合并（Landing work）
├── squash / PR body = commit message
├── trailer 必填（Following-Agents-Protocol / AI-Assisted / Assisted-by）
├── Closes #N
└── GitHub auto-close issue
↓
Post-merge state update
├── .agents/NOW.md
│   - ## Current work.State: TODO → DONE
│   - ## Current gate.Last push CI: pass at <commit-sha>
│   - ## Next actions.0: mark completed
│   -顶部 stamp: <!-- now-updated: YYYY-MM-DD -->
├── .agents/specs/<slug>.md
│   - 加 ## Outcome 段（status=DONE 必填）
│   - 测了什么、为什么 default 这样、拒绝了什么
├── issue│   - ✅ 不需手改（PR merge 自动 close）
├── .agents/project-context/ProjectTree.md: 更新文件树
└── commit message
    - ✅ trailer 已经在 commit 时填了
```