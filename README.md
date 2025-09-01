# OpenEvolve4J

A lightweight, flexible library for evolving code and prompts using an LLM‑driven MAP‑Elites engine. It treats a program or prompt as a “solution,” iteratively improves it with LLMs, evaluates it with your runner script, and explores a diverse archive of high‑performing variants. This project was inspired by [OpenEvolve](https://github.com/codelion/openevolve) and tries to implement [AlphaEvolve](https://arxiv.org/abs/2506.13131) algorithm.

## Highlights

- Strength in search: MAP‑Elites with islands, migration, Pareto dominance, feature scaling, and listener hooks.
- Domain-agnostic: applicable to any programming language or task. You can also use pure **MAPElites** for non LLM scenarios.
- Flexible evaluation: bring your own runner (any language) that prints metrics JSON to stdout.
- LLM‑agnostic: pluggable ensemble via Spring AI; point to OpenAI‑compatible APIs or local gateways.
- Simple config: one YAML controls models, prompts, metrics, grid dimensions etc.
- Extensible: customize prompts, metrics, selection ratios, bins, repository sizes, listeners, and much more.
- Robust: periodic checkpointing and restore support; built‑in diversity/complexity features.

Modules (monorepo):
- `openevolve4j-core` – the engine and Java API (most users start here).
- `openevolve4j-web`, `openevolve4j-ui` – optional extras (WIP).

## How it works

1) Selection chooses a parent plus inspirations from island/top/diverse pools.
2) Evolution uses an agent that prompts an LLM (diff or full‑rewrite) to propose the next solution.
3) Evaluation runs your script and extracts metrics from stdout (JSON).
4) Archiving bins solutions in a multi‑dimensional feature grid; a candidate replaces the cell if Pareto‑better.

Core entry points:
- `OpenEvolveConfig` – load from YAML, derive defaults.
- `OpenEvolve.create(config, objectMapper)` – builds a configured `MAPElites<EvolveSolution>` engine.
- `MAPElites#run(n)` – run N iterations; add listeners to observe/inspect.

## Quick start

### 1) Add the library

- JDK: 21+ (repo toolchain set to 24).
- Build locally and install:

```bash
./gradlew :openevolve4j-core:publishToMavenLocal
```

Then depend on `openevolve4j-core` from your app (ensure `mavenLocal()` is in your repositories).

### 2) Minimal runner

Your runner can be any executable. It must exit 0 and print a metrics JSON object to stdout. Example `run.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail
# Evaluate the current solution in $PWD (files already materialized by the engine)
# Produce metrics as JSON
# Minimal example:
echo '{"combined_score": 0.73, "latency_ms": 120}'
```

### 3) Create a config.yml

```yaml
promptPath: prompts   # optional: override built-in templates
llm:
  apiUrl: "http://localhost:4000"   # OpenAI-compatible endpoint
  apiKey: "${OPENAI_API_KEY}"        # or a local-gateway key
  models:
    - model: "gpt-4o-mini"
      temperature: 0.7
solution:
  path: solution           # directory containing the initial solution
  runner: run.sh           # invoked from the solution directory
  evalTimeout: PT60S       # ISO-8601 duration
  language: python         # used in prompts
  pattern: ".*\\.py$"       # regex for files to include
  fullRewrite: false       # prefer small diffs initially
selection:
  seed: 42
  explorationRatio: 0.1
  exploitationRatio: 0.3
  eliteSelectionRatio: 0.5
  numInspirations: 2
  numberDiverse: 5
  numberTop: 5
migration:
  rate: 0.1
  interval: 10
repository:
  checkpointInterval: 10
  populationSize: 200
  archiveSize: 50
  islands: 3
mapelites:
  numIterations: 100
  bins: 10
  dimensions: [score, complexity, diversity]  # or custom metrics you emit
metrics:
  combined_score: true     # Pareto objectives (true=maximize, false=minimize)
```

Notes:
- If `promptPath` is set, files whose names contain one of the keys below override built‑ins:
  - `system_default`, `user_diff`, `user_full_rewrite`, `task`, `solution`
- Built‑in feature dimensions: `score`, `complexity`, `diversity`.
  - Any other names in `dimensions` are read from your metrics JSON.

### 4) Run from Java

```java
import java.nio.file.Path;
import openevolve.Constants;
import openevolve.OpenEvolve;
import openevolve.OpenEvolveConfig;
import openevolve.mapelites.listener.MAPElitesLoggingListener;

public class Main {
  public static void main(String[] args) {
    var config = OpenEvolveConfig.fromFile(Path.of("config.yml"));
    var engine = OpenEvolve.create(config, Constants.OBJECT_MAPPER);
    engine.addListener(new MAPElitesLoggingListener<>());
    engine.run(config.mapelites().numIterations());
    engine.printArchive();
  }
}
```

## Short usage snippets

- Custom dimensions: add any metric you emit to `mapelites.dimensions`. The engine reads numbers from your JSON and bins them.
- Multi‑model ensemble: list multiple models under `llm.models`; the engine randomly samples one per evolution step for load balancing and diversity.
- Checkpoints: artifacts saved under `<solution-parent>/ckpt/checkpoint_iter_*.json`. You can restore by wiring a custom `CheckpointListener` with a target iteration.

## Configuration reference (compact)

- llm: `apiUrl`, `apiKey`, `models[]` (Spring AI `OpenAiChatOptions` fields like `model`, `temperature`).
- solution: `path`, `runner`, `evalTimeout`, `language`, `pattern` (regex), `fullRewrite`.
- selection: `explorationRatio`, `exploitationRatio`, `eliteSelectionRatio`, `numInspirations`, `numberDiverse`, `numberTop`, `seed`.
- repository: `populationSize`, `archiveSize`, `islands`, `checkpointInterval`.
- mapelites: `numIterations`, `bins`, `dimensions`.
- metrics: map of metricName -> `true` (maximize) | `false` (minimize), used by Pareto comparator.

## Tips

- Keep the runner fast and deterministic; use `evalTimeout` to bound slow runs.
- Start with fewer `bins`/`islands` for quick feedback; scale up later.
- Use `fullRewrite: true` to explore radically different approaches.

—

See `openevolve4j-core/src/test/resources/openevolve/` for sample configs, prompts, and runner scripts.
