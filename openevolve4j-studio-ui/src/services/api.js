/**
 * Base API service for OpenEvolve4j Studio
 */

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

class ApiError extends Error {
  constructor(message, status, response) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.response = response;
  }
}

/**
 * Base API service class providing common CRUD operations
 */
export class BaseApiService {
  constructor(basePath) {
    this.basePath = basePath;
    this.baseUrl = `${API_BASE_URL}${basePath}`;
  }

  async request(url, options = {}) {
    const config = {
      headers: {
        'Content-Type': 'application/json',
        ...options.headers,
      },
      ...options,
    };

    try {
      const response = await fetch(url, config);
      
      if (!response.ok) {
        const errorText = await response.text();
        throw new ApiError(
          `API request failed: ${response.status} ${response.statusText}`,
          response.status,
          errorText
        );
      }

      // Handle empty responses (e.g., DELETE operations)
      if (response.status === 204 || response.headers.get('content-length') === '0') {
        return null;
      }

      return await response.json();
    } catch (error) {
      if (error instanceof ApiError) {
        throw error;
      }
      throw new ApiError(`Network error: ${error.message}`, 0, null);
    }
  }

  /**
   * Get paginated list of resources with optional filtering and sorting
   * @param {Object} params - Query parameters
   * @param {string} params.filters - JSON string of filters
   * @param {string} params.sort - Sort field
   * @param {string} params.order - Sort order (asc/desc)
   * @param {number} params.offset - Pagination offset
   * @param {number} params.limit - Pagination limit
   */
  async getAll(params = {}) {
    const searchParams = new URLSearchParams();
    
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        searchParams.append(key, value);
      }
    });

    const url = `${this.baseUrl}${searchParams.toString() ? `?${searchParams.toString()}` : ''}`;
    return this.request(url);
  }

  /**
   * Get a single resource by ID
   */
  async getById(id) {
    return this.request(`${this.baseUrl}/${id}`);
  }

  /**
   * Create a new resource
   */
  async create(data) {
    return this.request(this.baseUrl, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  /**
   * Update an existing resource
   */
  async update(id, data) {
    return this.request(`${this.baseUrl}/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  /**
   * Delete a resource
   */
  async delete(id) {
    return this.request(`${this.baseUrl}/${id}`, {
      method: 'DELETE',
    });
  }
}

export { ApiError };
