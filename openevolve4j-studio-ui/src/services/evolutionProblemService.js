import { BaseApiService } from './api.js';

/**
 * Service for managing Evolution Problems
 */
export class EvolutionProblemService extends BaseApiService {
  constructor() {
    super('/evolution');
  }

  /**
   * Validate evolution problem data before creation/update
   */
  validateProblem(problem) {
    const errors = [];

    if (!problem.name || problem.name.trim() === '') {
      errors.push('Problem name is required');
    }

    if (!problem.config) {
      errors.push('Configuration is required');
    } else {
      // Validate config structure
      if (!problem.config.solution) {
        errors.push('Solution configuration is required');
      }
      if (!problem.config.mapelites) {
        errors.push('MAP-Elites configuration is required');
      }
      if (!problem.config.llm) {
        errors.push('LLM configuration is required');
      }
    }

    return errors;
  }

  /**
   * Create a new evolution problem with validation
   */
  async createProblem(problem) {
    const errors = this.validateProblem(problem);
    if (errors.length > 0) {
      throw new Error(`Validation failed: ${errors.join(', ')}`);
    }

    return this.create(problem);
  }

  /**
   * Update an existing evolution problem with validation
   */
  async updateProblem(id, problem) {
    const errors = this.validateProblem(problem);
    if (errors.length > 0) {
      throw new Error(`Validation failed: ${errors.join(', ')}`);
    }

    return this.update(id, problem);
  }

  /**
   * Get problems with specific filters
   */
  async searchProblems(searchTerm = '', sortBy = 'name', sortOrder = 'asc') {
    const filters = searchTerm ? { name: { $like: `%${searchTerm}%` } } : {};
    
    return this.getAll({
      filters: JSON.stringify(filters),
      sort: sortBy,
      order: sortOrder,
    });
  }
}

export const evolutionProblemService = new EvolutionProblemService();
