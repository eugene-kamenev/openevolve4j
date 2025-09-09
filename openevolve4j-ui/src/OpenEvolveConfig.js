export class Solution {
    constructor(params = {}) {
        this.workspace = params.workspace || "workspace";
        this.path = params.path || "solution";
        this.runner = params.runner || "run.sh";
        this.evalTimeout = params.evalTimeout || "PT120S";
        this.pattern = params.pattern || ".*\\.py$";
        this.language = params.language || "python";
        this.fullRewrite = params.fullRewrite ?? true;
    }
}

export class Selection {
    constructor(params = {}) {
        this.seed = params.seed ?? 42;
        this.explorationRatio = params.explorationRatio ?? 0.1;
        this.exploitationRatio = params.exploitationRatio ?? 0.1;
        this.eliteSelectionRatio = params.eliteSelectionRatio ?? 0.1;
        this.numInspirations = params.numInspirations ?? 5;
        this.numberDiverse = params.numberDiverse ?? 5;
        this.numberTop = params.numberTop ?? 5;
    }
}

export class Migration {
    constructor(params = {}) {
        this.rate = params.rate ?? 0.1;
        this.interval = params.interval ?? 10;
    }
}

export class Repository {
    constructor(params = {}) {
        this.checkpointInterval = params.checkpointInterval ?? 10;
        this.populationSize = params.populationSize ?? 50;
        this.archiveSize = params.archiveSize ?? 10;
        this.islands = params.islands ?? 2;
    }
}

export class MAPElites {
    constructor(params = {}) {
        this.numIterations = params.numIterations ?? 100;
        this.bins = params.bins ?? 10;
        this.dimensions = params.dimensions ?? ["score", "complexity", "diversity"];
    }
}

export class LLM {
    constructor(params = {}) {
        this.apiUrl = params.apiUrl || "";
        this.apiKey = params.apiKey || "";
        this.models = params.models || [];
    }
}

export class OpenEvolveConfig {
    constructor(params = {}) {
        this.promptPath = params.promptPath;
        this.solution = params.solution ? new Solution(params.solution) : new Solution();
        this.selection = params.selection ? new Selection(params.selection) : new Selection();
        this.migration = params.migration ? new Migration(params.migration) : new Migration();
        this.repository = params.repository ? new Repository(params.repository) : new Repository();
        this.mapelites = params.mapelites ? new MAPElites(params.mapelites) : new MAPElites();
        this.llm = params.llm ? new LLM(params.llm) : new LLM();
        this.metrics = params.metrics || {};
    }
}
