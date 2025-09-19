// Simple REST client matching backend routes in WebHandlers

const baseUrl = () => {
  const { protocol, host } = window.location;
  const hostname = host.split(':')[0];
  // Backend default port appears to be 7070 from WS; reuse for HTTP
  return `${protocol}//${hostname}:7070`;
};

const jsonHeaders = { 'Content-Type': 'application/json' };

const handleJson = async (res) => {
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `HTTP ${res.status}`);
  }
  if (res.status === 204) return null;
  return res.json();
};

// Generic helpers for the functional endpoints exposed by WebHandler.defaultRoutes
const list = (path, { filters, sort, order, offset, limit } = {}) => {
  const params = new URLSearchParams();
  if (filters) params.set('filters', JSON.stringify(filters));
  if (sort) params.set('sort', sort);
  if (order) params.set('order', order);
  if (offset != null) params.set('offset', String(offset));
  if (limit != null) params.set('limit', String(limit));
  return fetch(`${baseUrl()}${path}?${params.toString()}`, { headers: jsonHeaders })
    .then(handleJson);
};

const getById = (path, id) => fetch(`${baseUrl()}${path}/${id}`, { headers: jsonHeaders }).then(handleJson);
const create = (path, body) => fetch(`${baseUrl()}${path}`, { method: 'POST', headers: jsonHeaders, body: JSON.stringify(body) }).then(handleJson);
const update = (path, id, partial) => fetch(`${baseUrl()}${path}/${id}`, { method: 'PUT', headers: jsonHeaders, body: JSON.stringify(partial) }).then(handleJson);
const remove = (path, id) => fetch(`${baseUrl()}${path}/${id}`, { method: 'DELETE', headers: jsonHeaders }).then(handleJson);

// Problems (evolution problems)
export const ProblemsApi = {
  list: (opts) => list('/evolution', opts), // returns { list, count }
  get: (id) => getById('/evolution', id),
  create: (problem) => create('/evolution', problem),
  update: (id, partial) => update('/evolution', id, partial),
  remove: (id) => remove('/evolution', id),
};

// Runs
export const RunsApi = {
  list: (opts) => list('/run', opts),
  get: (id) => getById('/run', id),
  // Start requires StartCommand { problemId, runId?, solutionIds? }
  start: ({ problemId, runId, solutionIds }) => create('/run/start', { problemId, runId, solutionIds }),
  stop: (id) => create(`/run/${id}/stop`, {}),
  // Returns a map of { runId: status } for active runs
  status: () => fetch(`${baseUrl()}/run/status`, { headers: jsonHeaders }).then(handleJson),
};

// Solutions
export const SolutionsApi = {
  list: (opts) => list('/solution', opts),
  get: (id) => getById('/solution', id),
  listForRun: (runId, opts = {}) => list('/solution', { ...opts, filters: { forRun: runId, ...(opts.filters || {}) } }),
  getParents: (id) => fetch(`${baseUrl()}/solution/${id}/parents`, { headers: jsonHeaders }).then(handleJson),
};

// LLM Models
export const ModelsApi = {
  list: (opts) => list('/models', opts),
  get: (id) => getById('/models', id),
  create: (model) => create('/models', model),
  update: (id, partial) => update('/models', id, partial),
  remove: (id) => remove('/models', id),
};

// Evolution state
export const StateApi = {
  list: (opts) => list('/state', opts),
  get: (runId) => getById('/state', runId),
};

export const paginateAll = async (fn, opts = {}) => {
  const limit = opts.limit ?? 50;
  let offset = opts.offset ?? 0;
  const all = [];
  let total = 0;
  // Expecting endpoints to return { list, count }
  while (true) {
    const resp = await fn({ ...opts, limit, offset });
    const page = resp?.list ?? resp?.items ?? [];
    if (resp?.count != null) total = resp.count;
    all.push(...page);
    if (!page.length || (resp?.count != null && all.length >= resp.count)) break;
    offset += limit;
  }
  return { list: all, count: total || all.length };
};
