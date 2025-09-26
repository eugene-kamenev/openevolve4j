{research}

# Baseline Attempts

{parents}

{solution}

# Task
{task}

# Output Format

You MUST use **FIND/REPLACE** difference format shown below to indicate changes:

```
filename
<<<<<<< FIND
# Original code to find and replace (must match exactly)
=======
# New replacement code
>>>>>>> REPLACE
```
## DIFF FORMAT RULES:

Every *FIND/REPLACE* edit must use this format:
  1. Actual source code file name
  2. The start of search block: <<<<<<< FIND
  3. A contiguous chunk of lines to search for in the existing source code (including whitespace, comments etc.)
  4. The dividing line: =======
  5. The lines to replace into the source code
  6. The end of the replace block: >>>>>>> REPLACE

Example of valid diff format:

```
example.py
<<<<<<< FIND
for i in range(m):
    for j in range(p):
        for k in range(n):
            C[i, j] += A[i, k] * B[k, j]
=======
# Reorder loops for better memory access pattern
for i in range(m):
    for k in range(n):
        for j in range(p):
            C[i, j] += A[i, k] * B[k, j]
>>>>>>> REPLACE
```

## Critical Requirements

- Output only diff blocks: produce a sequence of valid FIND/REPLACE blocks and nothing else (no prose, no backticks around blocks, no explanations inline).
- Small, surgical edits: propose multiple small diffs instead of a single large one. Never replace an entire file or large unrelated regions.
- Exact-match search: each FIND block must match the current code exactly, including whitespace.
- Minimal scope: include only the lines that truly need to change; do not include surrounding lines that are unchanged, and do not reformat unrelated code.
- Split large changes: if a logical change spans many lines, split it into several small, self-contained diffs ordered from top-to-bottom within the file.
- One file per header: when many changes are in the same file, repeat the filename for each separate FIND/REPLACE block.

## Why Small Diffs

Small, exact diffs are safer and more reliable: they avoid accidental deletions, reduce merge conflicts, minimize hallucinations, and ensure patches apply cleanly in CI. Large full-file replacements are brittle and often fail to match the live code, breaking the patching process.

## Size Guidance

- Aim for ≤ 15–20 lines per FIND or REPLACE section.
- MUST split any change that would exceed ~25 lines into multiple targeted diffs.

## Do/Don’t

- Do: replace only the edited function body or the few lines around an insertion point.
- Do: add imports or constants by replacing a small, exact anchor line with that line plus the new line(s).
- Don’t: reformat files, reorder imports, or change whitespace outside the edited lines.
- Don’t: include license headers or unrelated refactors in the same submission.

## Submission Checklist

- Only diff blocks are output, no extra text.
- Every FIND block matches existing code exactly.
- No full-file replacements; changes are minimal and focused.
- Multiple small diffs are used where appropriate and ordered top-to-bottom.

You MUST focus on targeted improvements suggesting multiple small diffs instead of large ones, for the reasons above. Each FIND section must exactly match code in the current program.
