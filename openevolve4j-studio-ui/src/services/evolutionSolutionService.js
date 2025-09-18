import { BaseApiService } from './api.js';

/**
 * Service for managing Evolution Solutions
 */
export class EvolutionSolutionService extends BaseApiService {
  constructor() {
    super('/solution');
  }

  /**
   * Get all parent solutions for a given solution
   * @param {string} solutionId - UUID of the solution
   */
  async getParents(solutionId) {
    return this.request(`${this.baseUrl}/${solutionId}/parents`);
  }

  /**
   * Get solutions by run ID
   */
  async getSolutionsByRun(runId, sortBy = 'dateCreated', sortOrder = 'desc') {
    const filters = { runId };
    
    return this.getAll({
      filters: JSON.stringify(filters),
      sort: sortBy,
      order: sortOrder,
    });
  }

  /**
   * Get solutions by parent ID (children of a solution)
   */
  async getSolutionsByParent(forParent, sortBy = 'dateCreated', sortOrder = 'desc') {
    const filters = { forParent };
    
    return this.getAll({
      filters: JSON.stringify(filters),
      sort: sortBy,
      order: sortOrder,
    });
  }

  /**
   * Get top solutions by fitness
   */
  async getTopSolutions(limit = 10, fitnessMetric = 'score') {
    return this.getAll({
      // sort: `fitness.${fitnessMetric}`,
      // order: 'desc',
      limit,
    });
  }

  /**
   * Search solutions with complex filters
   */
  async searchSolutions(filters = {}, sortBy = 'dateCreated', sortOrder = 'desc', limit = 50) {
    return this.getAll({
      filters: Object.keys(filters).length ? JSON.stringify(filters) : undefined,
      sort: sortBy,
      order: sortOrder,
      limit,
    });
  }

  /**
   * Get solution lineage (ancestry tree)
   */
  async getSolutionLineage(solutionId) {
    const parents = await this.getParents(solutionId);
    const solution = await this.getById(solutionId);
    
    return {
      solution,
      parents,
      // Could extend to get children as well
    };
  }

  /**
   * Get solutions with best fitness in specific dimensions
   */
  async getBestSolutionsInDimension(dimension, runId = null, limit = 10) {
    const filters = runId ? { runId } : {};
    
    return this.getAll({
      filters: Object.keys(filters).length ? JSON.stringify(filters) : undefined,
      sort: `fitness.${dimension}`,
      order: 'desc',
      limit,
    });
  }

  /**
   * Compare two solutions
   */
  async compareSolutions(solutionId1, solutionId2) {
    const [solution1, solution2] = await Promise.all([
      this.getById(solutionId1),
      this.getById(solutionId2)
    ]);

    return {
      solution1,
      solution2,
      // Could add comparison metrics here
    };
  }
}

export const evolutionSolutionService = new EvolutionSolutionService();
