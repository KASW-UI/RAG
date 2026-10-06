#!/usr/bin/env python3
"""Keep .agents/NOW.md a short, current, one-Read resume surface.

RAG-NOW is a DOMAIN-ADAPTED version of vllm.md's NOW: structural contract
preserved (stamp + section existence + line budget + entry budget), but the
information model changed from "system live-claim snapshot" (vllm.md's
multi-row, multi-agent view) to "single-spec business-project pointer" (this
project). See the "Adaptations vs upstream" section in NOW.md for the
explicit mapping.

This checker owns one obligation: structure and budget, so NOW.md cannot
decay into a status log. The other half, "NOW must be refreshed when the
work moves", is owned by the spec and git history; this checker does not
duplicate it.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]

NOW = ROOT / ".agents/NOW.md"
NOW_PATH = ".agents/NOW.md"

# Budget rationale (RAG, not vllm.cpp):
#
# RAG is a single-spec business project. NOW entries are short: "module +
# state", "PR + commit", "next verb + path". Typical length 30-100 chars.
# MAX_LINES = 50 covers the 4-section shape (Current work / Current gate /
# Next actions / Protocol invariants) with headroom for an Adaptations
# table.
#
# MAX_ENTRY_CHARS = 200 is the local budget. vllm.md's 400 was tied to its
# row-digest entry shape (levers / commits / multi-figure results, 200-380
# chars typical); mechanically copying 400 into RAG would be a known
# anti-pattern. 200 gives headroom for an occasional complex entry while
# still flagging anything that has grown into a one-line narrative.
#
# MAX_CHARS IS NOT INTRODUCED. The whole-file budget was retired from
# vllm.md (ENG-RECORD-CONFLICT-SURFACES, #364) because it made NOW a
# surface every PR had to touch, and a shared-file budget that turns an
# ordinary edit into a read-modify-write of a single global is a known
# conflict surface. The reasoning applies equally to RAG.
MAX_LINES = 50
MAX_ENTRY_CHARS = 200

# Required sections. RAG NOW has 4 sections (not vllm.md's 3); vllm.md's
# "Live claims" maps to "Current work" (snapshot -> pointer).
REQUIRED_HEADINGS = (
    "current work",
    "current gate",
    "next actions",
    "protocol invariants",
)

# Sanity guard, not a primary check. RAG has no ROW-level stable IDs, but
# if a future agent tries to paste a per-row table back in (the very thing
# vllm.md retired in #374), this regex catches it before it decays NOW into
# a status log again. The shape is enforced, not just the state.
ROW_TABLE_LINE = re.compile(r"^\|\s*`[A-Z0-9][A-Za-z0-9_.-]*`\s*\|")

STAMP = re.compile(r"^<!--\s*now-updated:\s*(\d{4}-\d{2}-\d{2})\s*-->$", re.MULTILINE)


def structure_errors(text: str) -> list[str]:
    """Return budget/shape problems with the NOW digest."""
    errors: list[str] = []

    if not STAMP.search(text):
        errors.append(
            "missing the freshness stamp <!-- now-updated: YYYY-MM-DD -->; it "
            "records when this snapshot was last known true"
        )

    lowered = text.lower()
    for heading in REQUIRED_HEADINGS:
        if f"## {heading}" not in lowered:
            errors.append(
                f"missing the '## {heading}' section; a cold session needs all "
                f"of {', '.join(REQUIRED_HEADINGS)} to resume without reading "
                "the full record"
            )

    lines = text.splitlines()
    if len(lines) > MAX_LINES:
        errors.append(
            f"is {len(lines)} lines, over the {MAX_LINES}-line budget; move "
            "detail to the spec and keep only the live position here"
        )

    for lineno, line in enumerate(lines, 1):
        if ROW_TABLE_LINE.match(line.strip()):
            errors.append(
                f"line {lineno}: a per-row table row is back in NOW.md "
                f"({line.strip()[:48]!r}...). RAG is a single-spec project; "
                "if a multi-row digest appears here it means NOW has decayed "
                "into a status log. Move per-row detail to that row's own spec "
                "under `## Now`, which has one writer. Putting rows here again "
                "makes this file a surface every PR must write"
            )

    for line in lines:
        stripped = line.strip()
        if stripped.startswith(("-", "|")) and len(stripped) > MAX_ENTRY_CHARS:
            errors.append(
                f"an entry is {len(stripped)} characters, over the "
                f"{MAX_ENTRY_CHARS}-character budget: {stripped[:60]!r}...; "
                "link the spec instead of inlining the narrative"
            )

    return errors


def main(argv: list[str]) -> int:
    # --base/--head/--commit/--staged are accepted and ignored, same shape as
    # vllm.md's checker. CI invokes with a range; this check is range-
    # independent now that freshness coupling is owned by the spec/git side.
    # Silently accepting them keeps the CI invocation stable.
    del argv

    if not NOW.exists():
        print(f"ERROR: {NOW_PATH} does not exist", file=sys.stderr)
        return 1

    failures = [
        f"{NOW_PATH} {error}"
        for error in structure_errors(NOW.read_text(encoding="utf-8"))
    ]

    if failures:
        for failure in failures:
            print(f"ERROR: {failure}", file=sys.stderr)
        print(
            "NOW.md is the one-Read resume surface: the current work, the gate "
            "being chased, and the next actions, rewritten in place. Detail "
            "belongs in the spec; history belongs in git.",
            file=sys.stderr,
        )
        return 1

    print(f"OK: {NOW_PATH} is a current, in-budget resume digest.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))