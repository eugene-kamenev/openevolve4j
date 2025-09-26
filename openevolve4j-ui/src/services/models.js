import { apiRequest } from './apiClient.js';

export async function fetchModels({ query } = {}) {
  return apiRequest('/models', { query });
}

export async function fetchModel(id) {
  return apiRequest(`/models/${id}`);
}

export async function createModel(values) {
  return apiRequest('/models', {
    method: 'POST',
    body: values
  });
}

export async function updateModel(id, values) {
  return apiRequest(`/models/${id}`, {
    method: 'PUT',
    body: values
  });
}

export async function deleteModel(id) {
  return apiRequest(`/models/${id}`, {
    method: 'DELETE'
  });
}
