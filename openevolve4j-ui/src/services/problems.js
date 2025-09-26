import { apiRequest } from './apiClient.js';

export async function fetchProblems({ query } = {}) {
  return apiRequest('/problems', { query });
}

export async function fetchProblem(id) {
  return apiRequest(`/problems/${id}`);
}

export async function createProblem(values) {
  return apiRequest('/problems', {
    method: 'POST',
    body: values
  });
}

export async function updateProblem(id, values) {
  return apiRequest(`/problems/${id}`, {
    method: 'PUT',
    body: values
  });
}

export async function deleteProblem(id) {
  return apiRequest(`/problems/${id}`, {
    method: 'DELETE'
  });
}

export async function startProblem(id) {
  return apiRequest(`/problems/${id}/start`, {
    method: 'POST'
  });
}

export async function stopProblem(id) {
  return apiRequest(`/problems/${id}/stop`, {
    method: 'POST'
  });
}

export async function fetchProblemEvents(id, { query, filters } = {}) {
  return apiRequest(`/problems/${id}/events`, { query, filters });
}

export async function fetchProblemSolutions(id, { query, filters } = {}) {
  return apiRequest(`/problems/${id}/solutions`, { query, filters });
}

export async function fetchProblemRuns(id, { query, filters } = {}) {
  return apiRequest(`/problems/${id}/runs`, { query, filters });
}

export async function fetchProblemStatuses() {
  return apiRequest('/problems/status');
}

export async function fetchSolutionAncestors(problemId, solutionId, { query, filters } = {}) {
  return apiRequest(`/problems/${problemId}/solutions/${solutionId}/ancestors`, { query, filters });
}
