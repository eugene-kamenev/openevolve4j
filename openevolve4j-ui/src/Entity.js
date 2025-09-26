// Entity helpers aligned to backend records
// Backend entities:
// - Problem: { id: UUID, name: string, config: PuctTreeConfig }
// - LLMModel: { id: UUID, name: string }
// - Event<T>: { id: UUID, problemId: UUID, dateCreated: Instant, payload: T }
// - Event payload types: Solution, Run, Progress

export class Problem {
  constructor(params = {}) {
    this.id = params.id || null;
    this.name = params.name || '';
    this.config = params.config || createDefaultPuctTreeConfig();
  }
}

export class LLMModel {
  constructor(params = {}) {
    this.id = params.id || null;
    this.name = params.name || '';
  }
}

export class Event {
  constructor(params = {}) {
    this.id = params.id || null;
    this.problemId = params.problemId || null;
    this.dateCreated = params.dateCreated || null;
    this.payload = params.payload || {};
  }
}

// Event payload classes
export class Solution {
  constructor(params = {}) {
    this.id = params.id || null;
    this.parentId = params.parentId || null;
    this.runId = params.runId || null;
    this.data = params.data || {}; // SourceTree
    this.fitness = params.fitness || {};
  }
}

export class Run {
  constructor(params = {}) {
    this.id = params.id || null;
  }
}

export class Progress {
  constructor(params = {}) {
    this.runId = params.runId || null;
    this.message = params.message || '';
  }
}

// SourceTree data structure
export class SourceTree {
  constructor(params = {}) {
    this.files = params.files || {}; // Map<Path, String>
    this.metadata = params.metadata || {};
  }
  
  fullRewrite() {
    return this.metadata.fullRewrite || false;
  }

  model() {
    return this.metadata.llmModel || null;
  }
}

// Helper to create default PuctTreeConfig
export function createDefaultPuctTreeConfig() {
  return {
    solution: {
      workspace: "/tmp",
      path: "solution",
      runner: "runner",
      evalTimeout: "PT1M", // 1 minute
      fullRewrite: true,
      pattern: ".*\\.py$"
    },
    promptPath: null,
    llm: {
      models: []
    },
    iterations: 1000,
    explorationConstant: 0.1,
    metrics: {
      score: true
    }
  };
}

// Helper to create a new problem
export function createProblem(name = 'New Problem', config = null) {
  return new Problem({
    name,
    config: config || createDefaultPuctTreeConfig()
  });
}
