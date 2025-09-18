import { BaseApiService } from './api.js';

/**
 * Service for managing Evolution Runs
 */
export class EvolutionRunService extends BaseApiService {
  constructor() {
    super('/run');
  }

  /**
   * Start a new evolution run
   * @param {string} problemId - UUID of the evolution problem
   * @param {string} runId - UUID for the new run
   * @param {string[]} solutionIds - Array of solution UUIDs to start with
   */
  async startRun(problemId, runId = null, solutionIds = []) {
    const startCommand = {
      problemId,
      runId: runId,
      solutionIds: solutionIds?.length > 0 ? new Set(solutionIds) : null,
    };

    return this.request(`${this.baseUrl}/start`, {
      method: 'POST',
      body: JSON.stringify(startCommand),
    });
  }

  /**
   * Stop a running evolution process
   * @param {string} runId - UUID of the run to stop
   */
  async stopRun(runId) {
    return this.request(`${this.baseUrl}/${runId}/stop`, {
      method: 'POST',
    });
  }

  /**
   * Get runs filtered by problem ID
   */
  async getRunsByProblem(problemId, sortBy = 'dateCreated', sortOrder = 'desc') {
    const filters = { problemId };
    
    return this.getAll({
      filters: JSON.stringify(filters),
      sort: sortBy,
      order: sortOrder,
    });
  }

  /**
   * Get recent runs
   */
  async getRecentRuns(limit = 10) {
    return this.getAll({
      sort: 'dateCreated',
      order: 'desc',
      limit,
    });
  }

  /**
   * Get run with detailed information
   */
  async getRunDetails(runId) {
    return this.getById(runId);
  }

  /**
   * Search runs with various filters
   */
  async searchRuns(filters = {}, sortBy = 'dateCreated', sortOrder = 'desc', limit = 50) {
    return this.getAll({
      filters: Object.keys(filters).length ? JSON.stringify(filters) : undefined,
      sort: sortBy,
      order: sortOrder,
      limit,
    });
  }
}

export const evolutionRunService = new EvolutionRunService();
