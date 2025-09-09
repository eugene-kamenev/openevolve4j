import React, { useState, useEffect } from 'react';
import { X, Plus, Minus } from 'lucide-react';

const LLMModelModal = ({ 
  isOpen, 
  onClose, 
  onSave, 
  editingModel = null,
  title = 'Add LLM Model'
}) => {
  const [modelFormData, setModelFormData] = useState({ 
    name: '', 
    parameters: [
      { name: 'temperature', value: '0.7' },
      { name: 'max_tokens', value: '2048' }
    ] 
  });

  useEffect(() => {
    if (editingModel) {
      const parameters = [];
      Object.entries(editingModel).forEach(([key, value]) => {
        if (key !== 'model') {
          parameters.push({ name: key, value: value });
        }
      });
      
      setModelFormData({ 
        name: editingModel.model || '', 
        parameters: parameters 
      });
    } else {
      setModelFormData({ 
        name: '', 
        parameters: [
          { name: 'temperature', value: '0.7' },
          { name: 'max_tokens', value: '2048' }
        ] 
      });
    }
  }, [editingModel, isOpen]);

  const handleSave = () => {
    if (!modelFormData.name.trim()) {
      alert('Model name is required');
      return;
    }

    const newModel = { model: modelFormData.name };
    
    modelFormData.parameters.forEach(param => {
      if (param.name.trim() && param.value !== '') {
        let value = param.value;
        if (!isNaN(value) && !isNaN(parseFloat(value))) {
          value = parseFloat(value);
        } else if (value.toLowerCase() === 'true') {
          value = true;
        } else if (value.toLowerCase() === 'false') {
          value = false;
        }
        newModel[param.name.trim()] = value;
      }
    });

    onSave(newModel);
    onClose();
  };

  const addParameter = () => {
    setModelFormData(prev => ({
      ...prev,
      parameters: [...prev.parameters, { name: '', value: '' }]
    }));
  };

  const removeParameter = (index) => {
    setModelFormData(prev => ({
      ...prev,
      parameters: prev.parameters.filter((_, i) => i !== index)
    }));
  };

  const updateParameter = (index, field, value) => {
    setModelFormData(prev => ({
      ...prev,
      parameters: prev.parameters.map((param, i) => 
        i === index ? { ...param, [field]: value } : param
      )
    }));
  };

  if (!isOpen) return null;

  return (
    <div className="modal-overlay">
      <div className="modal-content">
        <div className="modal-header">
          <h3>{title}</h3>
          <button
            type="button"
            onClick={onClose}
            className="modal-close"
          >
            <X size={20} />
          </button>
        </div>
        
        <div className="modal-body">
          <div className="form-group">
            <label htmlFor="model-name">Model Name *</label>
            <input
              id="model-name"
              type="text"
              value={modelFormData.name}
              onChange={(e) => setModelFormData(prev => ({ ...prev, name: e.target.value }))}
              placeholder="e.g., gpt-4, claude-3, llama-3"
            />
          </div>

          <div className="form-group">
            <label>Parameters</label>
            <p className="field-help">Add custom parameters for this model. Common parameters include temperature, max_tokens, top_p, etc.</p>
            <div className="parameters-list">
              {modelFormData.parameters.map((param, index) => (
                <div key={index} className="parameter-row">
                  <input
                    type="text"
                    value={param.name}
                    onChange={(e) => updateParameter(index, 'name', e.target.value)}
                    placeholder="Parameter name (e.g., temperature)"
                  />
                  <input
                    type="text"
                    value={param.value}
                    onChange={(e) => updateParameter(index, 'value', e.target.value)}
                    placeholder="Value (e.g., 0.7, true, 150)"
                  />
                  <button
                    type="button"
                    onClick={() => removeParameter(index)}
                    className="btn-remove"
                  >
                    <Minus size={16} />
                  </button>
                </div>
              ))}
              <button
                type="button"
                onClick={addParameter}
                className="btn-add-param"
              >
                <Plus size={16} /> Add Parameter
              </button>
            </div>
          </div>
        </div>

        <div className="modal-footer">
          <button
            type="button"
            onClick={onClose}
            className="btn-cancel"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleSave}
            className="btn-save"
          >
            {editingModel ? 'Update Model' : 'Add Model'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default LLMModelModal;
