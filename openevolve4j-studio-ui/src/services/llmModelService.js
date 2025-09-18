import { BaseApiService } from './api.js';

/**
 * Service for managing LLM models
 */
export class LLMModelService extends BaseApiService {
  constructor() {
    super('/models');
  }

  /**
   * Get all available LLM models
   * @returns {Promise<Array>} Array of LLM model objects
   */
  async getAllModels() {
    return this.getAll({limit: 100});
  }

  /**
   * Get a specific LLM model by ID
   * @param {string} id - Model ID
   * @returns {Promise<Object>} LLM model object
   */
  async getModel(id) {
    return this.getById(id);
  }
}

// Export a singleton instance
export const llmModelService = new LLMModelService();
