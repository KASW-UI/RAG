#!/usr/bin/env python3
"""Enforce that README.md stays a human-readable, user-facing document.

Per AGENTS.md, README.md is the landing page for the RAG project, not a
status-tracking log. This checker fails if the README loses one of the
required user-facing sections (What is it / Features / Architecture /
Requirements / Build / Configuration / Usage or Run / API / Contributing),
grows a table cell into a "wall of prose", or contains an em-dash
(house style).

The validation logic is a pure function `readme_errors(text) -> list[str]`
so it is unit-testable and mutation-testable.
"""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
README = ROOT / "README.md"

# Each required user-facing section is (label, matchers): the README must have
# an H2 heading whose lowercased text contains ANY of the matcher substrings.
#
# Why these 9 (not vllm.md's 7): vllm.md's checker targets a C++/model/CUDA
# project (Features / Supported models / Performance / Build / CLI / OpenAI
# server / C API). RAG is a Java/Spring/gRPC business service; its landing
# page needs different anchors. Mechanical copy of vllm.md's 7 is a known
# anti-pattern (see .agents/style/domain-adaptation.md).
REQUIRED_SECTIONS: tuple[tuple[str, tuple[str, ...]], ...] = (
    ("What is it", ("what is it",)),
    ("Features", ("features",)),
    ("Architecture", ("architecture",)),
    ("Requirements", ("requirements",)),
    ("Build", ("build",)),
    ("Configuration", ("configuration",)),
    ("Usage / Run", ("usage", "run")),
    ("API", ("api",)),
    ("Contributing", ("contributing",)),
)

# A table cell longer than this is the "wall of prose" smell: forensic
# detail belongs in focused documentation, not in a README table cell.
MAX_CELL_CHARS = 220

# Per-paragraph budget. Kept at 900 (same as vllm.md's value) because the
# paragraph shape is project-independent: a single prose paragraph is the
# thing being bounded, not anything RAG-specific.
MAX_PARAGRAPH_CHARS = 900

CONTRIBUTOR_LINK = "CONTRIBUTING.md"


def _h2_headers(text: str) -> list[str]:
    return [ln[3:].strip() for ln in text.splitlines() if ln.startswith("## ")]


def _is_separator_row(cells: list[str]) -> bool:
    return all(set(cell) <= set("-: ") for cell in cells)


def _prose_paragraphs(text: str) -> list[tuple[int, str]]:
    """Yield (start_line, paragraph) for prose only.

    Fenced code blocks, tables, headings, and list items are excluded: the rule
    targets the wall-of-prose narrative paragraph, not legitimate long tables or
    code samples.
    """
    paragraphs: list[tuple[int, str]] = []
    current: list[str] = []
    start = 0
    in_fence = False

    def flush() -> None:
        if current:
            paragraphs.append((start, " ".join(current)))
            current.clear()

    for lineno, raw in enumerate(text.splitlines(), start=1):
        stripped = raw.strip()
        if stripped.startswith("```"):
            in_fence = not in_fence
            flush()
            continue
        if in_fence:
            continue
        is_prose = bool(stripped) and not (
            stripped.startswith("|")
            or stripped.startswith("#")
            or stripped.startswith("-")
            or stripped.startswith("*")
            or stripped.startswith(">")
        )
        if is_prose:
            if not current:
                start = lineno
            current.append(stripped)
        else:
            flush()
    flush()
    return paragraphs


def readme_errors(text: str) -> list[str]:
    """Return a list of human-readable problems with the README text."""
    errors: list[str] = []

    headers_lower = [h.lower() for h in _h2_headers(text)]
    for label, matchers in REQUIRED_SECTIONS:
        if not any(any(m in h for m in matchers) for h in headers_lower):
            errors.append(f"missing required user-facing section: {label}")

    if "—" in text:  # em-dash
        count = text.count("—")
        errors.append(
            f"README contains {count} em-dash(es); house style forbids them "
            "(use commas, periods, parentheses, or hyphens)"
        )

    if CONTRIBUTOR_LINK not in text:
        errors.append(
            f"README does not link to {CONTRIBUTOR_LINK}; contributors need a "
            "public entry point to the contributing protocol"
        )

    for lineno, para in _prose_paragraphs(text):
        if len(para) > MAX_PARAGRAPH_CHARS:
            errors.append(
                f"line {lineno}: prose paragraph of {len(para)} chars exceeds "
                f"{MAX_PARAGRAPH_CHARS} (wall-of-prose smell; move the detail "
                "to focused documentation and link to it)"
            )

    in_fence = False
    for lineno, raw in enumerate(text.splitlines(), start=1):
        stripped = raw.strip()
        if stripped.startswith("```"):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        if stripped.startswith("|") and stripped.endswith("|"):
            cells = [c.strip() for c in stripped.strip("|").split("|")]
            if _is_separator_row(cells):
                continue
            for cell in cells:
                if len(cell) > MAX_CELL_CHARS:
                    errors.append(
                        f"line {lineno}: table cell of {len(cell)} chars exceeds "
                        f"{MAX_CELL_CHARS} (wall-of-prose smell; move forensic "
                        "detail to focused documentation)"
                    )
    return errors


def main() -> int:
    if not README.exists():
        print("ERROR: README.md is missing", file=sys.stderr)
        return 1
    errors = readme_errors(README.read_text(encoding="utf-8"))
    if errors:
        print("ERROR: the user-facing docs are not valid:", file=sys.stderr)
        for err in errors:
            print(f"  - {err}", file=sys.stderr)
        return 1
    print("OK: README.md is a valid landing page and project overview.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())