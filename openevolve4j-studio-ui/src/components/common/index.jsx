import React from 'react';

/**
 * Loading spinner component
 */
export function LoadingSpinner({ size = 'medium', className = '' }) {
  const sizeClasses = {
    small: 'w-4 h-4',
    medium: 'w-8 h-8',
    large: 'w-12 h-12',
  };

  return (
    <div className={`flex justify-center items-center ${className}`}>
      <div className={`animate-spin rounded-full border-2 border-gray-300 border-t-blue-600 ${sizeClasses[size]}`}></div>
    </div>
  );
}

/**
 * Error message component
 */
export function ErrorMessage({ error, onRetry, className = '' }) {
  if (!error) return null;

  return (
    <div className={`bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md ${className}`}>
      <div className="flex justify-between items-start">
        <div>
          <h4 className="font-medium mb-1">Error</h4>
          <p className="text-sm">{error.message || 'An unexpected error occurred'}</p>
          {error.status && (
            <p className="text-xs mt-1 text-red-500">Status: {error.status}</p>
          )}
        </div>
        {onRetry && (
          <button
            onClick={onRetry}
            className="ml-4 text-sm text-red-700 hover:text-red-800 underline"
          >
            Retry
          </button>
        )}
      </div>
    </div>
  );
}

/**
 * Empty state component
 */
export function EmptyState({ title, description, action, icon, className = '' }) {
  return (
    <div className={`text-center py-12 ${className}`}>
      {icon && (
        <div className="mx-auto h-12 w-12 text-gray-400 mb-4">
          {icon}
        </div>
      )}
      <h3 className="mt-2 text-sm font-medium text-gray-900">{title}</h3>
      {description && (
        <p className="mt-1 text-sm text-gray-500">{description}</p>
      )}
      {action && (
        <div className="mt-6">
          {action}
        </div>
      )}
    </div>
  );
}

/**
 * Card component
 */
export function Card({ title, children, actions, className = '' }) {
  return (
    <div className={`bg-white shadow rounded-lg ${className}`}>
      {title && (
        <div className="px-4 py-5 sm:px-6 border-b border-gray-200">
          <div className="flex justify-between items-center">
            <h3 className="text-lg leading-6 font-medium text-gray-900">{title}</h3>
            {actions && <div className="flex space-x-2">{actions}</div>}
          </div>
        </div>
      )}
      <div className="px-4 py-5 sm:p-6">
        {children}
      </div>
    </div>
  );
}

/**
 * Button component
 */
export function Button({ 
  children, 
  variant = 'primary', 
  size = 'medium', 
  disabled = false, 
  loading = false,
  className = '',
  ...props 
}) {
  const baseClasses = 'inline-flex items-center justify-center font-medium rounded-md focus:outline-none focus:ring-2 focus:ring-offset-2 transition-colors';
  
  const variantClasses = {
    primary: 'bg-blue-600 text-white hover:bg-blue-700 focus:ring-blue-500 disabled:bg-blue-300',
    secondary: 'bg-gray-200 text-gray-900 hover:bg-gray-300 focus:ring-gray-500 disabled:bg-gray-100',
    danger: 'bg-red-600 text-white hover:bg-red-700 focus:ring-red-500 disabled:bg-red-300',
    success: 'bg-green-600 text-white hover:bg-green-700 focus:ring-green-500 disabled:bg-green-300',
  };

  const sizeClasses = {
    small: 'px-3 py-2 text-sm',
    medium: 'px-4 py-2 text-sm',
    large: 'px-6 py-3 text-base',
  };

  return (
    <button
      className={`${baseClasses} ${variantClasses[variant]} ${sizeClasses[size]} ${className}`}
      disabled={disabled || loading}
      {...props}
    >
      {loading && <LoadingSpinner size="small" className="mr-2" />}
      {children}
    </button>
  );
}

/**
 * Input component
 */
export function Input({ 
  label, 
  error, 
  required = false,
  className = '',
  ...props 
}) {
  return (
    <div className={className}>
      {label && (
        <label className="block text-sm font-medium text-gray-700 mb-1">
          {label}
          {required && <span className="text-red-500 ml-1">*</span>}
        </label>
      )}
      <input
        className={`block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm ${
          error ? 'border-red-300 focus:ring-red-500 focus:border-red-500' : ''
        }`}
        {...props}
      />
      {error && (
        <p className="mt-1 text-sm text-red-600">{error}</p>
      )}
    </div>
  );
}

/**
 * Select component
 */
export function Select({ 
  label, 
  error, 
  required = false,
  options = [],
  className = '',
  ...props 
}) {
  return (
    <div className={className}>
      {label && (
        <label className="block text-sm font-medium text-gray-700 mb-1">
          {label}
          {required && <span className="text-red-500 ml-1">*</span>}
        </label>
      )}
      <select
        className={`block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 sm:text-sm ${
          error ? 'border-red-300 focus:ring-red-500 focus:border-red-500' : ''
        }`}
        {...props}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      {error && (
        <p className="mt-1 text-sm text-red-600">{error}</p>
      )}
    </div>
  );
}

/**
 * Modal component
 */
export function Modal({ isOpen, onClose, title, children, maxWidth = 'max-w-md' }) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto">
      <div className="flex items-end justify-center min-h-screen pt-4 px-4 pb-20 text-center sm:block sm:p-0">
        <div className="fixed inset-0 bg-gray-500 bg-opacity-75 transition-opacity" onClick={onClose}></div>
        
        <span className="hidden sm:inline-block sm:align-middle sm:h-screen">&#8203;</span>
        
        <div className={`inline-block align-bottom bg-white rounded-lg text-left overflow-hidden shadow-xl transform transition-all sm:my-8 sm:align-middle ${maxWidth} sm:w-full`}>
          <div className="bg-white px-4 pt-5 pb-4 sm:p-6 sm:pb-4">
            <div className="flex justify-between items-center mb-4">
              <h3 className="text-lg font-medium text-gray-900">{title}</h3>
              <button
                onClick={onClose}
                className="text-gray-400 hover:text-gray-600"
              >
                <span className="sr-only">Close</span>
                ✕
              </button>
            </div>
            {children}
          </div>
        </div>
      </div>
    </div>
  );
}

export function MetadataViewer({ data, level = 0 }) {
  const [expandedKeys, setExpandedKeys] = useState(() => {
    // Initialize with first-level keys expanded
    const initialExpanded = new Set();
    if (level === 0 && data && typeof data === 'object' && !Array.isArray(data)) {
      initialExpanded.add('root');
    }
    return initialExpanded;
  });
  
  const toggleKey = (key) => {
    const newExpanded = new Set(expandedKeys);
    if (newExpanded.has(key)) {
      newExpanded.delete(key);
    } else {
      newExpanded.add(key);
    }
    setExpandedKeys(newExpanded);
  };

  const renderValue = (value, key = null, currentLevel = level) => {
    if (value === null || value === undefined) {
      return <span className="json-null">null</span>;
    }
    
    if (typeof value === 'string') {
      // Check if string contains newlines
      if (value.includes('\n')) {
        return <pre className="json-string multiline">{value}</pre>;
      }
      return <span className="json-string">{value}</span>;
    }
    
    if (typeof value === 'number') {
      return <span className="json-number">{value}</span>;
    }
    
    if (typeof value === 'boolean') {
      return <span className="json-boolean">{String(value)}</span>;
    }
    
    if (Array.isArray(value)) {
      const arrayKey = key || 'array';
      const isExpanded = expandedKeys.has(arrayKey);
      const isEmpty = value.length === 0;
      
      if (isEmpty) {
        return <span className="json-bracket">[]</span>;
      }
      
      return (
        <div className="json-container">
          <div
            className="json-header clickable"
            onClick={() => toggleKey(arrayKey)}
          >
            {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
            <span className="json-bracket">[</span>
            <span className="json-count">{value.length} {value.length === 1 ? 'item' : 'items'}</span>
            {!isExpanded && <span className="json-bracket">]</span>}
          </div>
          {isExpanded && (
            <div className="json-body">
              {value.map((item, idx) => (
                <div key={idx} className="json-item">
                  <span className="json-index">{idx}:</span>
                  {renderValue(item, `${arrayKey}[${idx}]`, currentLevel + 1)}
                  {idx < value.length - 1 && <span className="json-comma">,</span>}
                </div>
              ))}
              <span className="json-bracket">]</span>
            </div>
          )}
        </div>
      );
    }
    
    if (typeof value === 'object') {
      const objectKey = key || 'object';
      const isExpanded = expandedKeys.has(objectKey);
      const entries = Object.entries(value);
      const isEmpty = entries.length === 0;
      
      if (isEmpty) {
        return <span className="json-bracket">{'{}'}</span>;
      }
      
      return (
        <div className="json-container">
          <div
            className="json-header clickable"
            onClick={() => toggleKey(objectKey)}
          >
            {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
            <span className="json-bracket">{'{'}</span>
            <span className="json-count">{entries.length} {entries.length === 1 ? 'property' : 'properties'}</span>
            {!isExpanded && <span className="json-bracket">{'}'}</span>}
          </div>
          {isExpanded && (
            <div className="json-body">
              {entries.map(([objKey, objValue], idx) => (
                <div key={objKey} className="json-property">
                  <span className="json-key">"{objKey}":</span>
                  {renderValue(objValue, `${objectKey}.${objKey}`, currentLevel + 1)}
                  {idx < entries.length - 1 && <span className="json-comma">,</span>}
                </div>
              ))}
              <span className="json-bracket">{'}'}</span>
            </div>
          )}
        </div>
      );
    }
    
    return <span className="json-string">{String(value)}</span>;
  };

  return (
    <div className="json-viewer" style={{ '--level': level }}>
      {renderValue(data, 'root')}
    </div>
  );
};
