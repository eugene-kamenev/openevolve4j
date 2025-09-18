import { useState, useEffect, useCallback } from 'react';
import { ApiError } from '../services/index.jsx';

/**
 * Custom hook for managing API data fetching with loading and error states
 */
export function useApiData(apiFunction, dependencies = []) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    
    try {
      const result = await apiFunction();
      setData(result);
    } catch (err) {
      setError(err instanceof ApiError ? err : new Error(err.message));
    } finally {
      setLoading(false);
    }
  }, dependencies);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return {
    data,
    loading,
    error,
    refetch: fetchData,
  };
}

/**
 * Custom hook for managing paginated API data
 */
export function usePaginatedData(apiService, initialParams = {}) {
  const [data, setData] = useState([]);
  const [totalCount, setTotalCount] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [params, setParams] = useState({
    offset: 0,
    limit: 10,
    sort: 'dateCreated',
    order: 'desc',
    ...initialParams,
  });

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    
    try {
      const result = await apiService.getAll(params);
      setData(result.list || []);
      setTotalCount(result.count || 0);
    } catch (err) {
      setError(err instanceof ApiError ? err : new Error(err.message));
      setData([]);
      setTotalCount(0);
    } finally {
      setLoading(false);
    }
  }, [apiService, params]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const updateParams = useCallback((newParams) => {
    setParams(prev => ({ ...prev, ...newParams }));
  }, []);

  const nextPage = useCallback(() => {
    setParams(prev => ({
      ...prev,
      offset: prev.offset + prev.limit,
    }));
  }, []);

  const prevPage = useCallback(() => {
    setParams(prev => ({
      ...prev,
      offset: Math.max(0, prev.offset - prev.limit),
    }));
  }, []);

  const setPage = useCallback((page) => {
    setParams(prev => ({
      ...prev,
      offset: page * prev.limit,
    }));
  }, []);

  return {
    data,
    totalCount,
    loading,
    error,
    params,
    updateParams,
    nextPage,
    prevPage,
    setPage,
    refetch: fetchData,
    currentPage: Math.floor(params.offset / params.limit),
    totalPages: Math.ceil(totalCount / params.limit),
    hasNextPage: params.offset + params.limit < totalCount,
    hasPrevPage: params.offset > 0,
  };
}

/**
 * Custom hook for managing form state with validation
 */
export function useForm(initialState, validationRules = {}) {
  const [values, setValues] = useState(initialState);
  const [errors, setErrors] = useState({});
  const [touched, setTouched] = useState({});

  const setValue = useCallback((name, value) => {
    setValues(prev => ({ ...prev, [name]: value }));
    
    // Clear error when user starts typing
    if (errors[name]) {
      setErrors(prev => ({ ...prev, [name]: null }));
    }
  }, [errors]);

  const setFieldTouched = useCallback((name) => {
    setTouched(prev => ({ ...prev, [name]: true }));
  }, []);

  const validate = useCallback(() => {
    const newErrors = {};
    
    Object.entries(validationRules).forEach(([field, rules]) => {
      const value = values[field];
      
      if (rules.required && (!value || value.toString().trim() === '')) {
        newErrors[field] = `${field} is required`;
        return;
      }
      
      if (rules.minLength && value && value.length < rules.minLength) {
        newErrors[field] = `${field} must be at least ${rules.minLength} characters`;
        return;
      }
      
      if (rules.pattern && value && !rules.pattern.test(value)) {
        newErrors[field] = rules.message || `${field} is invalid`;
        return;
      }
      
      if (rules.custom && value) {
        const customError = rules.custom(value, values);
        if (customError) {
          newErrors[field] = customError;
          return;
        }
      }
    });
    
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  }, [values, validationRules]);

  const reset = useCallback(() => {
    setValues(initialState);
    setErrors({});
    setTouched({});
  }, [initialState]);

  return {
    values,
    errors,
    touched,
    setValue,
    setFieldTouched,
    validate,
    reset,
    isValid: Object.keys(errors).length === 0,
  };
}

/**
 * Custom hook for managing modal state
 */
export function useModal(initialState = false) {
  const [isOpen, setIsOpen] = useState(initialState);
  
  const open = useCallback(() => setIsOpen(true), []);
  const close = useCallback(() => setIsOpen(false), []);
  const toggle = useCallback(() => setIsOpen(prev => !prev), []);
  
  return {
    isOpen,
    open,
    close,
    toggle,
  };
}
