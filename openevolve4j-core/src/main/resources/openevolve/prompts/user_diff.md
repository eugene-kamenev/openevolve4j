# Parent Solutions

```xml
{parents}
```

# Current Solution

```xml
{solution}
```

# Task
{task}

# Output Format (STRICT)

Produce one or more SEARCH/REPLACE diffs. Do not include any prose outside the diff blocks.

Each diff block has this exact structure:

```
<path-to-file>
<<<<<<< SEARCH
<original code to replace — copied verbatim from Current Solution>
=======
<new code to replace the SEARCH block>
>>>>>>> REPLACE
```

Rules:
- Path
  - Use the actual file path relative to the repository root (e.g., src/pkg/file.py). No placeholders like path_to_solution/file.
  - For multiple edits in the same file, repeat the path header for each block.
- SEARCH block
  - Must match a single contiguous region in the Current Solution exactly, including whitespace, indentation, and comments. Copy-paste from {solution}.
  - Do not include ellipses, regex, wildcards, or additional context not present in the original.
  - If the SEARCH text occurs multiple times, make it unique by expanding the SEARCH region to include surrounding unique lines (still copied verbatim).
- REPLACE block
  - Contains only the replacement for the SEARCH region. Do not include unchanged surrounding code here.
  - Preserve formatting style (indentation, line endings) consistent with the file.
- Multiple changes
  - Output multiple diff blocks, one per replaced region. You may target multiple files.
- Insertions and deletions using SEARCH/REPLACE
  - Insertion: replace a small anchor region with “anchor + new code”.
    - Example pattern: SEARCH contains a unique anchor; REPLACE repeats the anchor followed by inserted lines.
  - Deletion: replace the exact block to remove with an empty replacement (leave the REPLACE section empty or a single blank line).
- Constraints
  - Do not rewrite entire files; keep changes minimal and targeted.
  - No explanations, comments, or extra text outside the fenced diff blocks.

## Examples

Single change in one file:
```
example.py
<<<<<<< SEARCH
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

Multiple changes in the same file (repeat the header):
```
app/service.py
<<<<<<< SEARCH
def add(a, b):
    return a + b
=======
def add(a, b):
    # Use safer numeric addition
    return (a or 0) + (b or 0)
>>>>>>> REPLACE
app/service.py
<<<<<<< SEARCH
def mul(a, b):
    return a*b
=======
def mul(a, b):
    # Guard against None
    return (a or 0) * (b or 0)
>>>>>>> REPLACE
```

Insertion via anchor expansion:
```
config.yaml
<<<<<<< SEARCH
features:
  - login
  - search
=======
features:
  - login
  - search
  - analytics
>>>>>>> REPLACE
```

Deletion (remove a block by replacing with nothing):
```
main.c
<<<<<<< SEARCH
printf("DEBUG: entering main\n");
=======
>>>>>>> REPLACE
```

# Guidelines
- Each SEARCH must match exact code in the Current Solution file, including whitespace. Prefer copying from {solution}.
- If a SEARCH is ambiguous (multiple matches), expand it to include nearby unique lines.
- Prefer changes that improve algorithmic metrics.
- Do not rewrite the entire program. Provide multiple diffs if needed.

# Checklist (before you output)
- [ ] File paths are correct and real.
- [ ] Every SEARCH is verbatim from Current Solution and unique.
- [ ] REPLACE contains only new/changed lines (no extra context).
- [ ] No prose outside fenced diff blocks.
- [ ] Minimal, targeted changes aligned with the Task.
