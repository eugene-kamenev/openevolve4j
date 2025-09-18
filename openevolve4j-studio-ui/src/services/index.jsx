// Re-export all services for easy importing
export { BaseApiService, ApiError } from './api.js';
export { EvolutionProblemService, evolutionProblemService } from './evolutionProblemService.js';
export { EvolutionRunService, evolutionRunService } from './evolutionRunService.js';
export { EvolutionSolutionService, evolutionSolutionService } from './evolutionSolutionService.js';
export { EvolutionStateService, evolutionStateService } from './evolutionStateService.js';
export { LLMModelService, llmModelService } from './llmModelService.js';

// Import service instances explicitly
import { evolutionProblemService } from './evolutionProblemService.js';
import { evolutionRunService } from './evolutionRunService.js';
import { evolutionSolutionService } from './evolutionSolutionService.js';
import { evolutionStateService } from './evolutionStateService.js';
import { llmModelService } from './llmModelService.js';

// Convenience object with all service instances
export const apiServices = {
  problems: evolutionProblemService,
  runs: evolutionRunService,
  solutions: evolutionSolutionService,
  states: evolutionStateService,
  models: llmModelService,
};
