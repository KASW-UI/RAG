# Git hooks

Repo-tracked hooks. They are not active until a clone opts in:

```sh
git config core.hooksPath .githooks
```

`git push --no-verify` bypasses them for one push.

## pre-push

Runs three gates against the **commits being pushed**, not the working tree:

- `scripts/check-readme-structure.py`
- `scripts/check-now-current.py`
- `scripts/check-prompt-contract.py`

All three enforce shapes that otherwise surface only in CI, and a push that
breaks one turns main red for whoever pushes next.

The hook refuses a push when it names a checker `scripts/` does not have.
If a future maintainer adds a checker to CHECKERS without committing its
script, push will fail closed rather than skip silently. Adding or removing
a checker is a one-line change to this hook's CHECKERS array and a matching
file in `scripts/`.

`scripts/agent-preflight.sh` runs the same three checkers plus`check-commit-style.py` and `check-commit-trailers.py` (RAG's preflight
NAMED_CHECKERS), and it stays the thing to run before committing. The hook
is the backstop for the run that gets skipped: it checks the pushed commit's
tree, so a dirty checkout cannot fail a clean push and an uncommitted fix
cannot let a broken commit through.