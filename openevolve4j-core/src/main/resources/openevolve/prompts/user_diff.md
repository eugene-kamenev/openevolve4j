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

# Output Format
Use diff-fenced format where

```
path_to_solution/file
<<<<<<< SEARCH
# Original code block to be found and replaced
=======
# New code block to replace the original
>>>>>>> REPLACE
```
## Example:

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

Ensure actual file path is used instead of **path_to_solution/file** placeholder.

Guidelines:
- Each SEARCH must match exact code in the Current Solution file, including whitespace.
- Prefer changes that improve algorithmic metrics.
- Do not rewrite the entire program. You can provide multiple diffs if needed.
