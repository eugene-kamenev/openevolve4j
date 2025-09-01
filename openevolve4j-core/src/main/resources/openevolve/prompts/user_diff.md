# Task
{task}

# Solution Parents
{parents}

{solution}

Required diff format:
<<<<<<< SEARCH
# Original code to find and replace (must match exactly)
=======
# New replacement code
>>>>>>> REPLACE

Example:
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

Guidelines:
- Each SEARCH section must match code in the current program exactly, including whitespace.
- Prefer changes that improve algorithmic metrics.
- Do not rewrite the entire program.
