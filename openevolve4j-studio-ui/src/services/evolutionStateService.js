import { BaseApiService } from './api.js';

/**
 * Service for managing Evolution States
 */
export class EvolutionStateService extends BaseApiService {
  constructor() {
    super('/state');
  }

  /**
   * Get evolution state by run ID
   * @param {string} runId - UUID of the evolution run
   */
  async getStateByRunId(runId) {
    const states = await this.getAll({
      filters: JSON.stringify({ runId }),
      sort: 'dateCreated',
      order: 'desc',
      limit: 1,
    });

    return states.list && states.list.length > 0 ? states.list[0] : null;
  }

  /**
   * Get state history for a run
   */
  async getStateHistory(runId, limit = 100) {
    return this.getAll({
      filters: JSON.stringify({ runId }),
      sort: 'dateCreated',
      order: 'desc',
      limit,
    });
  }

  /**
   * Get recent states across all runs
   */
  async getRecentStates(limit = 20) {
    return this.getAll({
      sort: 'dateCreated',
      order: 'desc',
      limit,
    });
  }

  /**
   * Get active runs (runs with recent state updates)
   */
  async getActiveRuns(minutesThreshold = 30) {
    const thresholdTime = new Date(Date.now() - minutesThreshold * 60 * 1000);
    const filters = {
      dateCreated: { $gte: thresholdTime.toISOString() }
    };

    return this.getAll({
      filters: JSON.stringify(filters),
      sort: 'dateCreated',
      order: 'desc',
    });
  }

  /**
   * Monitor state changes for a specific run
   * Returns a promise that resolves when state changes
   */
  async waitForStateChange(runId, currentStateTimestamp, timeoutMs = 30000) {
    const startTime = Date.now();
    
    while (Date.now() - startTime < timeoutMs) {
      const currentState = await this.getStateByRunId(runId);
      
      if (currentState && new Date(currentState.dateCreated) > new Date(currentStateTimestamp)) {
        return currentState;
      }
      
      // Wait 1 second before polling again
      await new Promise(resolve => setTimeout(resolve, 1000));
    }
    
    throw new Error('Timeout waiting for state change');
  }

  /**
   * Get state statistics
   */
  async getStateStatistics(runId = null) {
    const filters = runId ? { runId } : {};
    const states = await this.getAll({
      filters: Object.keys(filters).length ? JSON.stringify(filters) : undefined,
      limit: 1000, // Get more data for statistics
    });

    if (!states.list || states.list.length === 0) {
      return null;
    }

    // Basic statistics from the state data
    return {
      totalStates: states.count,
      latestState: states.list[0],
      oldestState: states.list[states.list.length - 1],
      stateFrequency: this.calculateStateFrequency(states.list),
    };
  }

  /**
   * Calculate how often states are being updated
   */
  calculateStateFrequency(states) {
    if (states.length < 2) return null;

    const timestamps = states.map(s => new Date(s.dateCreated).getTime()).sort();
    const intervals = [];
    
    for (let i = 1; i < timestamps.length; i++) {
      intervals.push(timestamps[i] - timestamps[i - 1]);
    }

    const averageInterval = intervals.reduce((sum, interval) => sum + interval, 0) / intervals.length;
    
    return {
      averageIntervalMs: averageInterval,
      averageIntervalSeconds: averageInterval / 1000,
      minIntervalMs: Math.min(...intervals),
      maxIntervalMs: Math.max(...intervals),
    };
  }
}

export const evolutionStateService = new EvolutionStateService();
