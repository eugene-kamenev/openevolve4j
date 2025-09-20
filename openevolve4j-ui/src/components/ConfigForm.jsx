import React, { useState, useEffect } from 'react';
import { Save, X, Info, Eye, Code, Settings, GitBranch, Database, Map, Cpu } from 'lucide-react';
import { OpenEvolveConfig, TreeConfig, createConfig } from '../Entity';
import YamlPreview from './YamlPreview';
import FormGroup from './forms/FormGroup';
import ArrayInput from './forms/ArrayInput';
import MetricsSection from './forms/MetricsSection';
import LLMSection from './forms/LLMSection';
import './ConfigForm.css';

const ConfigForm = ({ config, mode, onSave, onCancel }) => {
  const [formData, setFormData] = useState({
    name: '',
    type: 'MAPELITES',
    promptPath: '',
    solution: {},
    selection: {}, // MAPELITES only
    migration: {}, // MAPELITES only
    repository: {}, // MAPELITES only
    mapelites: {}, // MAPELITES only
    llm: {},
    metrics: {},
    // TREE-only
    llmGroups: [],
    iterations: 100,
    explorationConstant: 1.4
  });
  
  const [activeTab, setActiveTab] = useState('general');
  const [errors, setErrors] = useState({});
  const [showYamlPreview, setShowYamlPreview] = useState(false);

  useEffect(() => {
    if (config) {
      // Handle both cases: direct config object and wrapped config object
      const configData = config.config || config;
      
      // Convert old metrics format to new format if needed
      let metrics = {};
      if (configData.metrics) {
        metrics = { ...configData.metrics };
        // If metrics are in old format (boolean values indicating presence), convert them
        Object.keys(metrics).forEach(key => {
          if (typeof metrics[key] === 'boolean' && key !== 'maximize' && key !== 'minimize') {
            // Old format: if true, assume maximize; if false, remove the metric
            if (metrics[key]) {
              metrics[key] = true; // keep as maximize
            } else {
              delete metrics[key]; // remove if it was false
            }
          }
        });
      }
      
      setFormData({
        name: config.name || '',
        promptPath: configData.promptPath || '',
        solution: configData.solution ? { ...configData.solution } : {},
        selection: configData.selection ? { ...configData.selection } : {},
        migration: configData.migration ? { ...configData.migration } : {},
        repository: configData.repository ? { ...configData.repository } : {},
        mapelites: configData.mapelites ? { ...configData.mapelites } : {},
        llm: configData.llm ? { ...configData.llm } : {},
        type: configData.type || 'MAPELITES',
        metrics: metrics,
        llmGroups: configData.llmGroups || [],
        iterations: configData.iterations ?? 100,
        explorationConstant: configData.explorationConstant ?? 1.4
      });
    } else {
      // For new configs, provide some default metrics
      setFormData(prev => ({
        ...prev,
        metrics: {
          score: true,
          complexity: false,
          diversity: true
        }
      }));
    }
  }, [config]);

  const handleInputChange = (section, field, value) => {
    setFormData(prev => ({
      ...prev,
      [section]: {
        ...prev[section],
        [field]: value
      }
    }));
    
    if (errors[`${section}.${field}`]) {
      setErrors(prev => {
        const newErrors = { ...prev };
        delete newErrors[`${section}.${field}`];
        return newErrors;
      });
    }
  };

  const handleArrayChange = (section, field, index, value) => {
    setFormData(prev => {
      const newArray = [...(prev[section][field] || [])];
      newArray[index] = value;
      return {
        ...prev,
        [section]: {
          ...prev[section],
          [field]: newArray
        }
      };
    });
  };

  const addArrayItem = (section, field, defaultValue = '') => {
    setFormData(prev => ({
      ...prev,
      [section]: {
        ...prev[section],
        [field]: [...(prev[section][field] || []), defaultValue]
      }
    }));
  };

  const removeArrayItem = (section, field, index) => {
    setFormData(prev => {
      const newArray = [...(prev[section][field] || [])];
      newArray.splice(index, 1);
      return {
        ...prev,
        [section]: {
          ...prev[section],
          [field]: newArray
        }
      };
    });
  };

  const tabs = [
    { id: 'general', label: 'General', icon: Info },
    { id: 'solution', label: 'Solution', icon: Code },
    ...(formData.type === 'MAPELITES' ? [
      { id: 'evolution', label: 'Evolution', icon: GitBranch },
      { id: 'repository', label: 'Repository', icon: Database },
      { id: 'mapelites', label: 'MAP-Elites', icon: Map },
    ] : [
      { id: 'tree', label: 'Tree Search', icon: GitBranch },
    ]),
    { id: 'llm', label: 'LLM', icon: Cpu },
    { id: 'metrics', label: 'Metrics', icon: Settings }
  ];

  const renderGeneralTab = () => (
    <div className="tab-content">
      <div className="form-section">
        <h3>
          <Info size={20} />
          General Settings
        </h3>
        <FormGroup id="config-type" label="Configuration Type">
          <select
            id="config-type"
            value={formData.type}
            onChange={(e) => {
              const newType = e.target.value;
              setFormData(prev => ({
                ...prev,
                type: newType,
              }));
              // Switch tab to the type-specific tab to guide user
              setActiveTab(newType === 'MAPELITES' ? 'mapelites' : 'tree');
            }}
          >
            <option value="MAPELITES">MAPELITES</option>
            <option value="TREE">TREE</option>
          </select>
        </FormGroup>
        <FormGroup id="config-name" label="Configuration Name" required error={errors.name}>
          <input
            id="config-name"
            type="text"
            value={formData.name}
            onChange={(e) => setFormData(prev => ({ ...prev, name: e.target.value }))}
            className={errors.name ? 'error' : ''}
            placeholder="Enter configuration name"
          />
        </FormGroup>
        
        <FormGroup id="prompt-path" label="Prompt Path">
          <input
            id="prompt-path"
            type="text"
            value={formData.promptPath || ''}
            onChange={(e) => setFormData(prev => ({ ...prev, promptPath: e.target.value }))}
            placeholder="prompts"
          />
        </FormGroup>
      </div>
    </div>
  );

  const renderSolutionTab = () => (
    <div className="tab-content">
      <div className="form-section">
        <h3>
          <Code size={20} />
          Solution Configuration
        </h3>

        <FormGroup id="workspace-path" label="Workspace Path" required error={errors['solution.workspace']}>
          <input
            id="workspace-path"
            type="text"
            value={formData.solution.workspace || ''}
            onChange={(e) => handleInputChange('solution', 'workspace', e.target.value)}
            className={errors['solution.workspace'] ? 'error' : ''}
            placeholder="workspace"
          />
        </FormGroup>

        <FormGroup id="solution-path" label="Solution Path" required error={errors['solution.path']}>
          <input
            id="solution-path"
            type="text"
            value={formData.solution.path || ''}
            onChange={(e) => handleInputChange('solution', 'path', e.target.value)}
            className={errors['solution.path'] ? 'error' : ''}
            placeholder="solution"
          />
        </FormGroup>

        <FormGroup id="runner" label="Runner">
          <input
            id="runner"
            type="text"
            value={formData.solution.runner || ''}
            onChange={(e) => handleInputChange('solution', 'runner', e.target.value)}
            placeholder="run.sh"
          />
        </FormGroup>

        <FormGroup id="pattern" label="File Pattern">
          <input
            id="pattern"
            type="text"
            value={formData.solution.pattern || ''}
            onChange={(e) => handleInputChange('solution', 'pattern', e.target.value)}
            placeholder=".*\\.py$"
          />
        </FormGroup>

        <FormGroup 
          id="eval-timeout" 
          label="Evaluation Timeout"
          help="Use ISO 8601 duration format (e.g., PT120S for 120 seconds)"
        >
          <input
            id="eval-timeout"
            type="text"
            value={formData.solution.evalTimeout || 'PT120S'}
            onChange={(e) => handleInputChange('solution', 'evalTimeout', e.target.value)}
            placeholder="PT120S"
          />
        </FormGroup>

        <div className="form-group checkbox-group">
          <label>
            <input
              type="checkbox"
              checked={formData.solution.fullRewrite || false}
              onChange={(e) => handleInputChange('solution', 'fullRewrite', e.target.checked)}
            />
            Full Rewrite
          </label>
        </div>
      </div>
    </div>
  );

  const renderEvolutionTab = () => (
    <div className="tab-content">
      <div className="form-section">
        <h3>
          <GitBranch size={20} />
          Selection Parameters
        </h3>
        
        <div className="form-row">
          <FormGroup id="seed" label="Random Seed">
            <input
              id="seed"
              type="number"
              value={formData.selection.seed || 42}
              onChange={(e) => handleInputChange('selection', 'seed', parseInt(e.target.value))}
            />
          </FormGroup>

          <FormGroup id="exploration-ratio" label="Exploration Ratio">
            <input
              id="exploration-ratio"
              type="number"
              step="0.01"
              min="0"
              max="1"
              value={formData.selection.explorationRatio || 0.1}
              onChange={(e) => handleInputChange('selection', 'explorationRatio', parseFloat(e.target.value))}
            />
          </FormGroup>
        </div>

        <div className="form-row">
          <FormGroup id="exploitation-ratio" label="Exploitation Ratio">
            <input
              id="exploitation-ratio"
              type="number"
              step="0.01"
              min="0"
              max="1"
              value={formData.selection.exploitationRatio || 0.1}
              onChange={(e) => handleInputChange('selection', 'exploitationRatio', parseFloat(e.target.value))}
            />
          </FormGroup>

          <FormGroup id="elite-selection" label="Elite Selection Ratio">
            <input
              id="elite-selection"
              type="number"
              step="0.01"
              min="0"
              max="1"
              value={formData.selection.eliteSelectionRatio || 0.1}
              onChange={(e) => handleInputChange('selection', 'eliteSelectionRatio', parseFloat(e.target.value))}
            />
          </FormGroup>
        </div>

        <div className="form-row">
          <FormGroup id="num-inspirations" label="Number of Inspirations">
            <input
              id="num-inspirations"
              type="number"
              min="1"
              value={formData.selection.numInspirations || 5}
              onChange={(e) => handleInputChange('selection', 'numInspirations', parseInt(e.target.value))}
            />
          </FormGroup>

          <FormGroup id="number-diverse" label="Number Diverse">
            <input
              id="number-diverse"
              type="number"
              min="1"
              value={formData.selection.numberDiverse || 5}
              onChange={(e) => handleInputChange('selection', 'numberDiverse', parseInt(e.target.value))}
            />
          </FormGroup>

          <FormGroup id="number-top" label="Number Top">
            <input
              id="number-top"
              type="number"
              min="1"
              value={formData.selection.numberTop || 5}
              onChange={(e) => handleInputChange('selection', 'numberTop', parseInt(e.target.value))}
            />
          </FormGroup>
        </div>
      </div>

      <div className="form-section">
        <h3>
          <Settings size={20} />
          Migration Settings
        </h3>
        
        <div className="form-row">
          <FormGroup id="migration-rate" label="Migration Rate">
            <input
              id="migration-rate"
              type="number"
              step="0.01"
              min="0"
              max="1"
              value={formData.migration.rate || 0.1}
              onChange={(e) => handleInputChange('migration', 'rate', parseFloat(e.target.value))}
            />
          </FormGroup>

          <FormGroup id="migration-interval" label="Migration Interval">
            <input
              id="migration-interval"
              type="number"
              min="1"
              value={formData.migration.interval || 10}
              onChange={(e) => handleInputChange('migration', 'interval', parseInt(e.target.value))}
            />
          </FormGroup>
        </div>
      </div>
    </div>
  );

  const renderRepositoryTab = () => (
    <div className="tab-content">
      <div className="form-section">
        <h3>
          <Database size={20} />
          Repository Configuration
        </h3>
        
        <div className="form-row">
          <FormGroup id="population-size" label="Population Size">
            <input
              id="population-size"
              type="number"
              min="1"
              value={formData.repository.populationSize || 50}
              onChange={(e) => handleInputChange('repository', 'populationSize', parseInt(e.target.value))}
            />
          </FormGroup>

          <FormGroup id="archive-size" label="Archive Size">
            <input
              id="archive-size"
              type="number"
              min="1"
              value={formData.repository.archiveSize || 10}
              onChange={(e) => handleInputChange('repository', 'archiveSize', parseInt(e.target.value))}
            />
          </FormGroup>
        </div>

        <div className="form-row">
          <FormGroup id="islands" label="Number of Islands">
            <input
              id="islands"
              type="number"
              min="1"
              value={formData.repository.islands || 2}
              onChange={(e) => handleInputChange('repository', 'islands', parseInt(e.target.value))}
            />
          </FormGroup>

          <FormGroup id="checkpoint-interval" label="Checkpoint Interval">
            <input
              id="checkpoint-interval"
              type="number"
              min="1"
              value={formData.repository.checkpointInterval || 10}
              onChange={(e) => handleInputChange('repository', 'checkpointInterval', parseInt(e.target.value))}
            />
          </FormGroup>
        </div>
      </div>
    </div>
  );

  const renderMapElitesTab = () => (
    <div className="tab-content">
      <div className="form-section">
        <h3>
          <Map size={20} />
          MAP-Elites Configuration
        </h3>
        
        <div className="form-row">
          <FormGroup id="num-iterations" label="Number of Iterations">
            <input
              id="num-iterations"
              type="number"
              min="1"
              value={formData.mapelites.numIterations || 100}
              onChange={(e) => handleInputChange('mapelites', 'numIterations', parseInt(e.target.value))}
            />
          </FormGroup>

          <FormGroup id="bins" label="Number of Bins">
            <input
              id="bins"
              type="number"
              min="1"
              value={formData.mapelites.bins || 10}
              onChange={(e) => handleInputChange('mapelites', 'bins', parseInt(e.target.value))}
            />
          </FormGroup>
        </div>

        <FormGroup label="Dimensions">
          <ArrayInput
            items={formData.mapelites.dimensions || []}
            onAdd={() => addArrayItem('mapelites', 'dimensions', 'new_dimension')}
            onRemove={(index) => removeArrayItem('mapelites', 'dimensions', index)}
            onChange={(index, value) => handleArrayChange('mapelites', 'dimensions', index, value)}
            placeholder="Dimension name"
            addLabel="Add Dimension"
          />
        </FormGroup>
      </div>
    </div>
  );

  const renderTreeTab = () => (
    <div className="tab-content">
      <div className="form-section">
        <h3>
          <GitBranch size={20} />
          Tree Search Configuration
        </h3>

        <div className="form-row">
          <FormGroup id="tree-iterations" label="Iterations">
            <input
              id="tree-iterations"
              type="number"
              min="1"
              value={formData.iterations ?? 100}
              onChange={(e) => setFormData(prev => ({ ...prev, iterations: parseInt(e.target.value) }))}
            />
          </FormGroup>
          <FormGroup id="tree-cpuct" label="Exploration Constant (cPUCT)">
            <input
              id="tree-cpuct"
              type="number"
              step="0.1"
              min="0"
              value={formData.explorationConstant ?? 1.4}
              onChange={(e) => setFormData(prev => ({ ...prev, explorationConstant: parseFloat(e.target.value) }))}
            />
          </FormGroup>
        </div>

        <FormGroup label="LLM Groups (by model id)">
          <div className="array-input">
            {(formData.llmGroups || []).map((group, gi) => (
              <div key={gi} className="array-item">
                <div className="array-input">
                  {(group || []).map((modelId, mi) => (
                    <div key={mi} className="array-item">
                      <input
                        type="text"
                        value={modelId}
                        placeholder="model-id"
                        onChange={(e) => {
                          const val = e.target.value;
                          setFormData(prev => {
                            const groups = [...(prev.llmGroups || [])].map(arr => [...arr]);
                            groups[gi][mi] = val;
                            return { ...prev, llmGroups: groups };
                          });
                        }}
                      />
                      <button type="button" className="btn-remove" onClick={() => {
                        setFormData(prev => {
                          const groups = [...(prev.llmGroups || [])].map(arr => [...arr]);
                          groups[gi].splice(mi, 1);
                          return { ...prev, llmGroups: groups };
                        });
                      }}>Remove</button>
                    </div>
                  ))}
                  <button type="button" className="btn-add" onClick={() => {
                    setFormData(prev => {
                      const groups = [...(prev.llmGroups || [])].map(arr => [...arr]);
                      groups[gi] = [...(groups[gi] || []), ''];
                      return { ...prev, llmGroups: groups };
                    });
                  }}>Add Model</button>
                </div>
                <button type="button" className="btn-remove" onClick={() => {
                  setFormData(prev => {
                    const groups = [...(prev.llmGroups || [])];
                    groups.splice(gi, 1);
                    return { ...prev, llmGroups: groups };
                  });
                }}>Remove Group</button>
              </div>
            ))}
            <button type="button" className="btn-add" onClick={() => {
              setFormData(prev => ({ ...prev, llmGroups: [...(prev.llmGroups || []), ['']] }));
            }}>Add Group</button>
          </div>
        </FormGroup>
      </div>
    </div>
  );

  const renderLLMTab = () => (
    <div className="tab-content">
      <LLMSection
        data={formData.llm}
        onChange={(field, value) => handleInputChange('llm', field, value)}
        errors={errors}
      />
    </div>
  );

  const renderMetricsTab = () => (
    <div className="tab-content">
      <MetricsSection
        metrics={formData.metrics}
        onChange={(newMetrics) => setFormData(prev => ({ ...prev, metrics: newMetrics }))}
      />
    </div>
  );

  const renderTabContent = () => {
    switch (activeTab) {
      case 'general': return renderGeneralTab();
      case 'solution': return renderSolutionTab();
      case 'evolution': return renderEvolutionTab();
      case 'repository': return renderRepositoryTab();
      case 'mapelites': return renderMapElitesTab();
      case 'tree': return renderTreeTab();
      case 'llm': return renderLLMTab();
      case 'metrics': return renderMetricsTab();
      default: return renderGeneralTab();
    }
  };

  const validateForm = () => {
    const newErrors = {};
    
    if (!formData.name?.trim()) {
      newErrors['name'] = 'Configuration name is required';
    }
    
    if (!formData.solution?.path?.trim()) {
      newErrors['solution.path'] = 'Solution path is required';
    }
    
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    
    if (!validateForm()) {
      return;
    }

    try {
      const base = {
        promptPath: formData.promptPath,
        solution: formData.solution,
        llm: formData.llm,
        metrics: formData.metrics,
        type: formData.type
      };
      let configToSave;
      if (formData.type === 'TREE') {
        configToSave = new TreeConfig({
          ...base,
          llmGroups: formData.llmGroups,
          iterations: formData.iterations,
          explorationConstant: formData.explorationConstant
        });
      } else {
        configToSave = new OpenEvolveConfig({
          ...base,
          selection: formData.selection,
          migration: formData.migration,
          repository: formData.repository,
          mapelites: formData.mapelites
        });
      }

      onSave({
        ...configToSave,
        name: formData.name
      });
    } catch (error) {
      console.error('Error creating configuration:', error);
      alert('Error creating configuration. Please check your inputs.');
    }
  };

  return (
    <div className="config-form">
      <div className="form-header">
        <h2>{mode === 'create' ? 'Create New Configuration' : 'Edit Configuration'}</h2>
      </div>

      <div className="form-tabs">
        {tabs.map(tab => (
          <button
            key={tab.id}
            className={`tab-button ${activeTab === tab.id ? 'active' : ''}`}
            onClick={() => setActiveTab(tab.id)}
          >
            {tab.icon && <tab.icon size={16} />}
            {tab.label}
          </button>
        ))}
      </div>

      <form onSubmit={handleSubmit} className="config-form-content">
        {renderTabContent()}

        <div className="form-actions">
          <button type="button" onClick={onCancel} className="btn btn-secondary">
            <X size={18} />
            Cancel
          </button>
          <button type="submit" className="btn btn-primary">
            <Save size={18} />
            {mode === 'create' ? 'Create Configuration' : 'Save Changes'}
          </button>
        </div>
      </form>
    </div>
  );
};

export default ConfigForm;
