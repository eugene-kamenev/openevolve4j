import React, { useState } from 'react';
import { Plus, Minus, Eye } from 'lucide-react';
import FormGroup from './FormGroup';
import LLMModelModal from './LLMModelModal';

const LLMSection = ({ data, onChange, errors = {} }) => {
  const [showModelModal, setShowModelModal] = useState(false);
  const [editingModelIndex, setEditingModelIndex] = useState(null);

  const handleModelSave = (model) => {
    const newModels = [...(data.models || [])];
    
    if (editingModelIndex !== null) {
      newModels[editingModelIndex] = model;
    } else {
      newModels.push(model);
    }
    
    onChange('models', newModels);
    setEditingModelIndex(null);
  };

  const addModel = () => {
    setEditingModelIndex(null);
    setShowModelModal(true);
  };

  const editModel = (index) => {
    setEditingModelIndex(index);
    setShowModelModal(true);
  };

  const removeModel = (index) => {
    const newModels = [...(data.models || [])];
    newModels.splice(index, 1);
    onChange('models', newModels);
  };

  return (
    <div className="form-section">
      <h3>LLM Configuration</h3>
      
      <FormGroup
        id="api-url"
        label="API URL"
        required
        error={errors['llm.apiUrl']}
      >
        <input
          id="api-url"
          type="url"
          value={data.apiUrl || ''}
          onChange={(e) => onChange('apiUrl', e.target.value)}
          className={errors['llm.apiUrl'] ? 'error' : ''}
          placeholder="http://localhost:4000"
        />
      </FormGroup>

      <FormGroup id="api-key" label="API Key">
        <input
          id="api-key"
          type="password"
          value={data.apiKey || ''}
          onChange={(e) => onChange('apiKey', e.target.value)}
          placeholder="Your API key"
        />
      </FormGroup>

      <FormGroup label="Models">
        <div className="array-input">
          {(data.models || []).map((model, index) => (
            <div key={index} className="array-item model-item">
              <div className="model-summary">
                <strong>{model.model || 'Unnamed Model'}</strong>
                <div className="model-params">
                  {Object.entries(model).filter(([key]) => key !== 'model').map(([key, value]) => (
                    <span key={key} className="param-tag">
                      {key}: {String(value)}
                    </span>
                  ))}
                </div>
              </div>
              <div className="model-actions">
                <button
                  type="button"
                  onClick={() => editModel(index)}
                  className="btn-edit"
                  title="Edit Model"
                >
                  <Eye size={16} />
                </button>
                <button
                  type="button"
                  onClick={() => removeModel(index)}
                  className="btn-remove"
                  title="Remove Model"
                >
                  <Minus size={16} />
                </button>
              </div>
            </div>
          ))}
          <button
            type="button"
            onClick={addModel}
            className="btn-add"
          >
            <Plus size={16} /> Add Model
          </button>
        </div>
      </FormGroup>

      <LLMModelModal
        isOpen={showModelModal}
        onClose={() => setShowModelModal(false)}
        onSave={handleModelSave}
        editingModel={editingModelIndex !== null ? data.models[editingModelIndex] : null}
        title={editingModelIndex !== null ? 'Edit LLM Model' : 'Add LLM Model'}
      />
    </div>
  );
};

export default LLMSection;
