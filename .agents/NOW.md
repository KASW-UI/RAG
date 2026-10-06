# NOW — RAG project current state

<!-- now-updated: 2026-10-05 -->

Snapshot, not log. History is git.

## Current work
- Spec: `specs/rag-ingest.md` (待写)
- State: `TODO`

## Current gate
- Last `agent-preflight.sh`: n/a (no commit yet)
- Last push CI: n/a (no commit yet)

## Next actions
0. 写第一个 spec `specs/rag-ingest.md`
1. operator 检查 TODO → 派 implementer 起草 ingest parser interface
2. implementer 完成 → operator 派 reviewer 验证
3. reviewer PASS → operator 合并

## Protocol invariants
- operator → implementer → reviewer → operator
- trailer block required
- landed spec requires `## Outcome`
- README paragraph ≤ 900 chars
- README table cell ≤ 220 chars
- no em-dash