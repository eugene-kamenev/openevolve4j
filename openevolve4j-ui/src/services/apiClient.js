const DEFAULT_HEADERS = {
  'Content-Type': 'application/json'
};

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:7070';

function buildUrl(path, query, filters) {
  const url = new URL(path.startsWith('http') ? path : `${API_BASE_URL}${path}`);
  if (filters && Object.keys(filters).length > 0) {
    query = { ...query, filters: JSON.stringify(filters) };
  }
  if (query) {
    Object.entries(query)
      .filter(([, value]) => value !== undefined && value !== null && value !== '')
      .forEach(([key, value]) => url.searchParams.set(key, value));
  }
  return url.toString();
}

export async function apiRequest(path, { method = 'GET', body, headers, query, filters } = {}) {
  const url = buildUrl(path, query, filters);
  const response = await fetch(url, {
    method,
    headers: {
      ...DEFAULT_HEADERS,
      ...headers
    },
    body: body !== undefined ? JSON.stringify(body) : undefined
  });

  if (!response.ok) {
    const message = await safeParseError(response);
    throw new Error(message || `Request failed with status ${response.status}`);
  }

  if (response.status === 204) {
    return null;
  }

  const contentType = response.headers.get('content-type') ?? '';
  if (contentType.includes('application/json')) {
    return response.json();
  }
  return response.text();
}

async function safeParseError(response) {
  try {
    const contentType = response.headers.get('content-type') ?? '';
    if (contentType.includes('application/json')) {
      const payload = await response.json();
      return payload.message || JSON.stringify(payload);
    }
    return await response.text();
  } catch (error) {
    console.error('Failed to parse error response', error);
    return null;
  }
}

export function deduplicateById(items = []) {
  const seen = new Set();
  return items.filter((item) => {
    if (!item || !item.id) return true;
    if (seen.has(item.id)) return false;
    seen.add(item.id);
    return true;
  });
}
