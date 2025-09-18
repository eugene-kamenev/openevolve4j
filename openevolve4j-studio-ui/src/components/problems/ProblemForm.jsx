import React, { useState, useEffect } from 'react';
import { useForm } from '../../hooks/index.jsx';
import { evolutionProblemService, llmModelService } from '../../services/index.jsx';
import { Modal, Button, Input, Select, ErrorMessage } from '../common/index.jsx';

export default function ProblemForm({ isOpen, onClose, onSuccess, problem = null }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [availableModels, setAvailableModels] = useState([]);
  const [activeTab, setActiveTab] = useState('basic');

  const isEdit = !!problem;

  const tabs = [
    { id: 'basic', label: 'Basic Info', icon: '📝' },
    { id: 'solution', label: 'Solution', icon: '⚙️' },
    { id: 'evolution', label: 'Evolution', icon: '🧬' },
    { id: 'llm', label: 'LLM Config', icon: '🤖' },
    { id: 'metrics', label: 'Metrics', icon: '📊' }
  ];

  const initialValues = {
    name: problem?.name || '',
    pattern: problem?.config?.solution?.pattern || '.*\\.py$',
    workspace: problem?.config?.solution?.workspace || '/tmp/workspace',
    fullRewrite: problem?.config?.solution?.fullRewrite ?? true,
    evalTimeoutMinutes: problem?.config?.solution?.evalTimeout ? 
      Math.floor(problem.config.solution.evalTimeout / 60000) : 1,
    
    // MAP-Elites config
    numIterations: problem?.config?.mapelites?.numIterations || 100,
    bins: problem?.config?.mapelites?.bins || 10,
    dimensions: problem?.config?.mapelites?.dimensions?.join(', ') || 'score, complexity, diversity',
    
    // Repository config
    populationSize: problem?.config?.repository?.populationSize || 50,
    archiveSize: problem?.config?.repository?.archiveSize || 10,
    islands: problem?.config?.repository?.islands || 2,
    checkpointInterval: problem?.config?.repository?.checkpointInterval || 10,
    
    // Selection config
    explorationRatio: problem?.config?.selection?.explorationRatio || 0.1,
    exploitationRatio: problem?.config?.selection?.exploitationRatio || 0.1,
    eliteSelectionRatio: problem?.config?.selection?.eliteSelectionRatio || 0.1,
    numInspirations: problem?.config?.selection?.numInspirations || 5,
    
    // LLM config - multiple models
    llmModels: problem?.config?.llm?.models 
      ? problem.config.llm.models.map(model => ({
          model: model.model || '',
          options: Object.entries(model)
            .filter(([key]) => key !== 'model')
            .map(([key, value]) => ({ key, value: String(value) }))
        }))
      : [{ 
          model: '', 
          options: [{ key: 'temperature', value: '0.7' }] 
        }],
    
    // Custom metrics
    metrics: problem?.config?.metrics || {
      score: true
    },
  };

  const validationRules = {
    name: { required: true, minLength: 3 },
    numIterations: { required: true, custom: (value) => value < 1 ? 'Must be at least 1' : null },
    bins: { required: true, custom: (value) => value < 1 ? 'Must be at least 1' : null },
    populationSize: { required: true, custom: (value) => value < 1 ? 'Must be at least 1' : null },
    llmModels: { 
      required: true, 
      custom: (models) => {
        if (!models || models.length === 0) return 'At least one model is required';
        const hasValidModel = models.some(m => m.model && m.model.trim() !== '');
        return hasValidModel ? null : 'At least one model must be selected';
      }
    },
  };

  const { values, errors, setValue, validate, reset } = useForm(initialValues, validationRules);

  // Helper function to check which tabs have validation errors
  const getTabErrors = () => {
    const tabErrors = {
      basic: ['name'],
      solution: [],
      evolution: ['numIterations', 'bins', 'populationSize'],
      llm: ['llmModels'],
      metrics: []
    };
    
    const errorsByTab = {};
    Object.keys(tabErrors).forEach(tab => {
      errorsByTab[tab] = tabErrors[tab].some(field => errors[field]);
    });
    
    return errorsByTab;
  };

  const tabErrors = getTabErrors();

  // Load available models on component mount
  useEffect(() => {
    const loadModels = async () => {
      try {
        const models = await llmModelService.getAllModels();
        setAvailableModels(models.list || []);
      } catch (err) {
        console.error('Failed to load LLM models:', err);
        setAvailableModels([]);
      }
    };

    if (isOpen) {
      loadModels();
    }
  }, [isOpen]);

  // Custom metrics management
  const addCustomMetric = () => {
    const newMetrics = { ...values.metrics, '': true };
    setValue('metrics', newMetrics);
  };

  const removeCustomMetric = (metricName) => {
    const newMetrics = { ...values.metrics };
    delete newMetrics[metricName];
    setValue('metrics', newMetrics);
  };

  const updateCustomMetric = (oldName, newName, maximize) => {
    const newMetrics = { ...values.metrics };
    if (oldName !== newName) {
      delete newMetrics[oldName];
    }
    newMetrics[newName] = maximize;
    setValue('metrics', newMetrics);
  };

  // Multiple LLM models management
  const addLLMModel = () => {
    const newModels = [...values.llmModels, { 
      model: '', 
      options: [{ key: 'temperature', value: '0.7' }] 
    }];
    setValue('llmModels', newModels);
  };

  const removeLLMModel = (index) => {
    const newModels = values.llmModels.filter((_, i) => i !== index);
    setValue('llmModels', newModels);
  };

  const updateLLMModel = (index, field, value) => {
    const newModels = values.llmModels.map((model, i) => 
      i === index ? { ...model, [field]: value } : model
    );
    setValue('llmModels', newModels);
  };

  const addLLMOption = (modelIndex) => {
    const newModels = values.llmModels.map((model, i) => 
      i === modelIndex 
        ? { ...model, options: [...model.options, { key: '', value: '' }] }
        : model
    );
    setValue('llmModels', newModels);
  };

  const removeLLMOption = (modelIndex, optionIndex) => {
    const newModels = values.llmModels.map((model, i) => 
      i === modelIndex 
        ? { ...model, options: model.options.filter((_, j) => j !== optionIndex) }
        : model
    );
    setValue('llmModels', newModels);
  };

  const updateLLMOption = (modelIndex, optionIndex, field, value) => {
    const newModels = values.llmModels.map((model, i) => 
      i === modelIndex 
        ? {
            ...model, 
            options: model.options.map((option, j) => 
              j === optionIndex ? { ...option, [field]: value } : option
            )
          }
        : model
    );
    setValue('llmModels', newModels);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    if (!validate()) {
      return;
    }

    setLoading(true);
    setError(null);

    try {
      // Build the configuration object
      const config = {
        solution: {
          pattern: values.pattern,
          workspace: values.workspace,
          fullRewrite: values.fullRewrite,
          evalTimeout: values.evalTimeoutMinutes * 60000, // Convert to milliseconds
          path: "solution",
          runner: "run.sh",
        },
        mapelites: {
          numIterations: parseInt(values.numIterations),
          bins: parseInt(values.bins),
          dimensions: values.dimensions.split(',').map(d => d.trim()).filter(d => d),
        },
        repository: {
          populationSize: parseInt(values.populationSize),
          archiveSize: parseInt(values.archiveSize),
          islands: parseInt(values.islands),
          checkpointInterval: parseInt(values.checkpointInterval),
        },
        selection: {
          explorationRatio: parseFloat(values.explorationRatio),
          exploitationRatio: parseFloat(values.exploitationRatio),
          eliteSelectionRatio: parseFloat(values.eliteSelectionRatio),
          numInspirations: parseInt(values.numInspirations),
          numberDiverse: 5,
          numberTop: 5,
          seed: 42,
        },
        migration: {
          rate: 0.1,
          interval: 10,
        },
        llm: {
          models: values.llmModels
        .filter(llmModel => llmModel.model && llmModel.model.trim() !== '')
        .map(llmModel => ({
          model: llmModel.model,
          ...llmModel.options
            .filter(option => option.key && option.value !== undefined && option.value !== '')
            .reduce((acc, option) => {
          // Try to parse as number, fallback to string
          const numValue = Number(option.value);
          acc[option.key] = !isNaN(numValue) && option.value !== '' ? numValue : option.value;
          return acc;
            }, {})
        }))
        },
        metrics: values.metrics
      };

      const problemData = {
        name: values.name,
        config,
      };

      if (isEdit) {
        await evolutionProblemService.updateProblem(problem.id, problemData);
      } else {
        await evolutionProblemService.createProblem(problemData);
      }

      onSuccess();
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    reset();
    setError(null);
    setActiveTab('basic');
    onClose();
  };

  return (
    <Modal 
      isOpen={isOpen} 
      onClose={handleClose} 
      title={isEdit ? 'Edit Problem' : 'Create Problem'}
      maxWidth="max-w-4xl"
    >
      <form onSubmit={handleSubmit} className="space-y-6">
        {error && <ErrorMessage error={error} />}

        {/* Tab Navigation */}
        <div className="border-b border-gray-200">
          <nav className="-mb-px flex space-x-8">
            {tabs.map((tab) => (
              <button
                key={tab.id}
                type="button"
                onClick={() => setActiveTab(tab.id)}
                className={`
                  flex items-center gap-2 py-2 px-1 border-b-2 font-medium text-sm relative
                  ${activeTab === tab.id
                    ? 'border-blue-500 text-blue-600'
                    : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                  }
                `}
              >
                <span>{tab.icon}</span>
                {tab.label}
                {tabErrors[tab.id] && (
                  <span className="w-2 h-2 bg-red-500 rounded-full ml-1"></span>
                )}
              </button>
            ))}
          </nav>
        </div>

        {/* Tab Content */}
        <div className="mt-6">
          {activeTab === 'basic' && (
            <div className="space-y-4">
              <h3 className="text-lg font-medium text-gray-900 mb-4">Basic Information</h3>
              <Input
                label="Problem Name"
                required
                value={values.name}
                onChange={(e) => setValue('name', e.target.value)}
                error={errors.name}
                placeholder="Enter a descriptive name for this problem"
              />
            </div>
          )}

          {activeTab === 'solution' && (
            <div className="space-y-4">
              <h3 className="text-lg font-medium text-gray-900 mb-4">Solution Configuration</h3>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Input
                  label="Workspace Folder"
                  value={values.workspace}
                  onChange={(e) => setValue('workspace', e.target.value)}
                  placeholder="/tmp/workspace"
                />
                <Input
                  label="Solution Root Folder"
                  value={values.solution}
                  onChange={(e) => setValue('solution', e.target.value)}
                  placeholder="solution"
                />
                <Input
                  label="Runner Script"
                  value={values.runner}
                  onChange={(e) => setValue('runner', e.target.value)}
                  placeholder="run.sh"
                />
                <Input
                  label="File Pattern"
                  value={values.pattern}
                  onChange={(e) => setValue('pattern', e.target.value)}
                  placeholder=".*\\.py$"
                />
                <Input
                  label="Evaluation Timeout (minutes)"
                  type="number"
                  min="1"
                  value={values.evalTimeoutMinutes}
                  onChange={(e) => setValue('evalTimeoutMinutes', parseInt(e.target.value))}
                />
                <div className="flex items-center col-span-1 md:col-span-2">
                  <input
                    type="checkbox"
                    id="fullRewrite"
                    checked={values.fullRewrite}
                    onChange={(e) => setValue('fullRewrite', e.target.checked)}
                    className="mr-2"
                  />
                  <label htmlFor="fullRewrite" className="text-sm text-gray-700">
                    Full Rewrite Mode
                  </label>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'evolution' && (
            <div className="space-y-6">
              <h3 className="text-lg font-medium text-gray-900 mb-4">Evolution Configuration</h3>
              
              {/* MAP-Elites Configuration */}
              <div>
                <h4 className="text-md font-medium text-gray-800 mb-3">MAP-Elites</h4>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <Input
                    label="Number of Iterations"
                    type="number"
                    min="1"
                    required
                    value={values.numIterations}
                    onChange={(e) => setValue('numIterations', parseInt(e.target.value))}
                    error={errors.numIterations}
                  />
                  <Input
                    label="Bins"
                    type="number"
                    min="1"
                    required
                    value={values.bins}
                    onChange={(e) => setValue('bins', parseInt(e.target.value))}
                    error={errors.bins}
                  />
                  <Input
                    label="Dimensions"
                    value={values.dimensions}
                    onChange={(e) => setValue('dimensions', e.target.value)}
                    placeholder="score, complexity, diversity"
                  />
                </div>
              </div>

              {/* Repository Configuration */}
              <div className="border-t pt-4">
                <h4 className="text-md font-medium text-gray-800 mb-3">Repository</h4>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                  <Input
                    label="Population Size"
                    type="number"
                    min="1"
                    required
                    value={values.populationSize}
                    onChange={(e) => setValue('populationSize', parseInt(e.target.value))}
                    error={errors.populationSize}
                  />
                  <Input
                    label="Archive Size"
                    type="number"
                    min="1"
                    value={values.archiveSize}
                    onChange={(e) => setValue('archiveSize', parseInt(e.target.value))}
                  />
                  <Input
                    label="Islands"
                    type="number"
                    min="1"
                    value={values.islands}
                    onChange={(e) => setValue('islands', parseInt(e.target.value))}
                  />
                  <Input
                    label="Checkpoint Interval"
                    type="number"
                    min="1"
                    value={values.checkpointInterval}
                    onChange={(e) => setValue('checkpointInterval', parseInt(e.target.value))}
                  />
                </div>
              </div>

              {/* Selection Configuration */}
              <div className="border-t pt-4">
                <h4 className="text-md font-medium text-gray-800 mb-3">Selection</h4>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                  <Input
                    label="Exploration Ratio"
                    type="number"
                    step="0.01"
                    min="0"
                    max="1"
                    value={values.explorationRatio}
                    onChange={(e) => setValue('explorationRatio', parseFloat(e.target.value))}
                  />
                  <Input
                    label="Exploitation Ratio"
                    type="number"
                    step="0.01"
                    min="0"
                    max="1"
                    value={values.exploitationRatio}
                    onChange={(e) => setValue('exploitationRatio', parseFloat(e.target.value))}
                  />
                  <Input
                    label="Elite Selection Ratio"
                    type="number"
                    step="0.01"
                    min="0"
                    max="1"
                    value={values.eliteSelectionRatio}
                    onChange={(e) => setValue('eliteSelectionRatio', parseFloat(e.target.value))}
                  />
                  <Input
                    label="Number of Inspirations"
                    type="number"
                    min="1"
                    value={values.numInspirations}
                    onChange={(e) => setValue('numInspirations', parseInt(e.target.value))}
                  />
                </div>
              </div>
            </div>
          )}

          {activeTab === 'llm' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-lg font-medium text-gray-900">LLM Configuration</h3>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={addLLMModel}
                >
                  Add Model
                </Button>
              </div>
              
              {errors.llmModels && (
                <div className="text-red-600 text-sm">{errors.llmModels}</div>
              )}
              
              {values.llmModels.length === 0 && (
                <p className="text-sm text-gray-500 italic">
                  No models configured. Click "Add Model" to add one.
                </p>
              )}
              
              <div className="space-y-6">
                {values.llmModels.map((llmModel, modelIndex) => (
                  <div key={modelIndex} className="border border-gray-200 rounded-lg p-4 bg-gray-50">
                    <div className="flex items-center justify-between mb-4">
                      <h5 className="text-md font-medium text-gray-800">
                        Model {modelIndex + 1}
                      </h5>
                      {values.llmModels.length > 1 && (
                        <Button
                          type="button"
                          variant="danger"
                          size="sm"
                          onClick={() => removeLLMModel(modelIndex)}
                        >
                          Remove Model
                        </Button>
                      )}
                    </div>

                    {/* Model Selection */}
                    <div className="mb-4">
                      <Select
                        label="Model"
                        required
                        value={llmModel.model}
                        onChange={(e) => updateLLMModel(modelIndex, 'model', e.target.value)}
                        options={[
                          { value: '', label: 'Select a model' },
                          ...availableModels.map(model => ({
                            value: model.name,
                            label: model.name
                          }))
                        ]}
                      />
                    </div>

                    {/* Model Options */}
                    <div>
                      <div className="flex items-center justify-between mb-2">
                        <label className="block text-sm font-medium text-gray-700">
                          Additional Options (OpenAI Chat Options)
                        </label>
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={() => addLLMOption(modelIndex)}
                        >
                          Add Option
                        </Button>
                      </div>
                      
                      <div className="space-y-2">
                        {llmModel.options.map((option, optionIndex) => (
                          <div key={optionIndex} className="flex gap-2 items-center">
                            <Input
                              placeholder="Key (e.g., temperature, maxTokens)"
                              value={option.key}
                              onChange={(e) => updateLLMOption(modelIndex, optionIndex, 'key', e.target.value)}
                              className="flex-1"
                            />
                            <Input
                              placeholder="Value (e.g., 0.7, 100)"
                              value={option.value}
                              onChange={(e) => updateLLMOption(modelIndex, optionIndex, 'value', e.target.value)}
                              className="flex-1"
                            />
                            <Button
                              type="button"
                              variant="outline"
                              size="sm"
                              onClick={() => removeLLMOption(modelIndex, optionIndex)}
                              className="text-red-600 hover:text-red-800"
                            >
                              Remove
                            </Button>
                          </div>
                        ))}
                        
                        {llmModel.options.length === 0 && (
                          <p className="text-sm text-gray-500 italic">
                            No additional options configured. Click "Add Option" to add custom OpenAI chat options.
                          </p>
                        )}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {activeTab === 'metrics' && (
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-lg font-medium text-gray-900">Metrics Configuration</h3>
                <Button
                  type="button"
                  onClick={addCustomMetric}
                  variant="secondary"
                  size="sm"
                >
                  Add Metric
                </Button>
              </div>
              
              {Object.keys(values.metrics).length === 0 && (
                <p className="text-sm text-gray-500 italic">
                  No metrics defined. Click "Add Metric" to add one.
                </p>
              )}
              
              <div className="space-y-3">
                {Object.entries(values.metrics).map(([metricName, maximize], index) => (
                  <div key={`${metricName}-${index}`} className="flex items-center gap-4 p-3 border border-gray-200 rounded-lg bg-gray-50">
                    <div className="flex-1">
                      <Input
                        label={`Metric ${index + 1} Name`}
                        value={metricName}
                        onChange={(e) => updateCustomMetric(metricName, e.target.value, maximize)}
                        placeholder="Enter metric name (e.g., 'score', 'complexity', 'performance')"
                        className="mb-0"
                      />
                    </div>
                    <div className="flex items-center gap-2">
                      <label className="text-sm text-gray-700 whitespace-nowrap">
                        Objective:
                      </label>
                      <Select
                        value={maximize ? 'maximize' : 'minimize'}
                        onChange={(e) => updateCustomMetric(metricName, metricName, e.target.value === 'maximize')}
                        options={[
                          { value: 'maximize', label: 'Maximize' },
                          { value: 'minimize', label: 'Minimize' },
                        ]}
                        className="min-w-[120px]"
                      />
                    </div>
                    <Button
                      type="button"
                      onClick={() => removeCustomMetric(metricName)}
                      variant="danger"
                      size="sm"
                    >
                      Remove
                    </Button>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Actions */}
        <div className="flex justify-end space-x-4 pt-6 border-t">
          <Button
            type="button"
            onClick={handleClose}
            variant="secondary"
          >
            Cancel
          </Button>
          <Button
            type="submit"
            loading={loading}
            disabled={loading}
          >
            {isEdit ? 'Update Problem' : 'Create Problem'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
