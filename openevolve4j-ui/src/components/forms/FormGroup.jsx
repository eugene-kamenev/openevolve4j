import React from 'react';

const FormGroup = ({ 
  id, 
  label, 
  children, 
  error, 
  required = false, 
  help, 
  className = '' 
}) => {
  return (
    <div className={`form-group ${className}`}>
      <label htmlFor={id}>
        {label}
        {required && ' *'}
      </label>
      {children}
      {help && <small className="field-help">{help}</small>}
      {error && <div className="field-error">{error}</div>}
    </div>
  );
};

export default FormGroup;
