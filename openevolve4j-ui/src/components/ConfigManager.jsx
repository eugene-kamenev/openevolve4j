import React, { useState, useContext } from 'react';
import { Save, Upload, Download, Plus, Edit, Trash2, Copy, Settings } from 'lucide-react';
import { OpenEvolveConfig, Solution, Selection, Migration, Repository, MAPElites, LLM } from '../OpenEvolveConfig';
import ConfigForm from './ConfigForm';
import ConfigList from './ConfigList';
import './ConfigManager.css';
import yaml from 'js-yaml';
import { ConfigContext } from '../App';

const ConfigManager = () => {
  const { configs, setConfigs, sendWsMessage, sendWsRequest } = useContext(ConfigContext);
  const [selectedConfig, setSelectedConfig] = useState(null);
  const [isEditing, setIsEditing] = useState(false);
  const [viewMode, setViewMode] = useState('list'); // 'list', 'edit', 'create'
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const createDefaultConfig = () => {
    return new OpenEvolveConfig({
      promptPath: "prompts",
      solution: {
        path: "solution",
        runner: "run.sh",
        evalTimeout: "PT120S",
        fullRewrite: true,
        language: "python",
        pattern: ".*\\.py$"
      },
      selection: {
        seed: 42,
        explorationRatio: 0.1,
        exploitationRatio: 0.1,
        eliteSelectionRatio: 0.1,
        numInspirations: 5,
        numberDiverse: 5,
        numberTop: 5
      },
      migration: {
        rate: 0.1,
        interval: 10
      },
      repository: {
        checkpointInterval: 10,
        populationSize: 50,
        archiveSize: 10,
        islands: 2
      },
      mapelites: {
        numIterations: 100,
        bins: 10,
        dimensions: ["score", "complexity", "diversity"]
      },
      llm: {
        models: [
          { model: "gpt-4", temperature: 0.7 },
          { model: "claude-3", temperature: 0.8 }
        ],
        apiUrl: "https://api.openai.com/v1",
        apiKey: ""
      },
      metrics: {
        score: true,
        complexity: true,
        diversity: true,
        performance: false
      }
    });
  };

  const handleCreateNew = () => {
    setSelectedConfig(createDefaultConfig());
    setViewMode('create');
    setIsEditing(true);
  };

  const handleEdit = (config) => {
    setSelectedConfig(config);
    setViewMode('edit');
    setIsEditing(true);
  };

  const handleSave = async (configData) => {
    setLoading(true);
    setError(null);
    
    try {
      if (viewMode === 'create') {
        const configId = Date.now().toString();
        
        // Send CREATE_CONFIG request and wait for response
        const response = await sendWsRequest({
          type: 'CONFIG_CREATE',
          id: configId,
          config: configData
        });
        
        // Update local state with the created config
        const newConfig = {
          id: configId,
          name: configData.name || `Config ${configs.length + 1}`,
          created: new Date().toISOString(),
          modified: new Date().toISOString(),
          config: configData
        };
        setConfigs(prev => [...prev, newConfig]);
        
      } else if (viewMode === 'edit') {
        // Send UPDATE_CONFIG request and wait for response
        const response = await sendWsRequest({
          type: 'CONFIG_UPDATE',
          id: selectedConfig.id,
          config: configData
        });
        
        // Update local state with the updated config
        const updatedConfig = {
          ...selectedConfig,
          config: configData,
          modified: new Date().toISOString()
        };
        setConfigs(prev => prev.map(c => 
          c.id === selectedConfig.id ? updatedConfig : c
        ));
      }
      
      setViewMode('list');
      setIsEditing(false);
      setSelectedConfig(null);
    } catch (error) {
      console.error('Error saving configuration:', error);
      setError(`Failed to save configuration: ${error.message}`);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (configId) => {
    if (window.confirm('Are you sure you want to delete this configuration?')) {
      setLoading(true);
      setError(null);
      
      try {
        // Send DELETE_CONFIG request and wait for response
        await sendWsRequest({
          type: 'CONFIG_DELETE',
          id: configId
        });
        
        // Update local state
        setConfigs(configs.filter(c => c.id !== configId));
      } catch (error) {
        console.error('Error deleting configuration:', error);
        setError(`Failed to delete configuration: ${error.message}`);
      } finally {
        setLoading(false);
      }
    }
  };

  const handleDuplicate = async (config) => {
    setLoading(true);
    setError(null);
    
    try {
      const configId = Date.now().toString();
      const duplicatedConfig = {
        ...config.config,
        name: `${config.name} (Copy)`
      };
      
      // Send CREATE_CONFIG request and wait for response
      await sendWsRequest({
        type: 'CONFIG_CREATE',
        id: configId,
        config: duplicatedConfig
      });
      
      // Update local state
      const newConfig = {
        id: configId,
        name: duplicatedConfig.name,
        created: new Date().toISOString(),
        modified: new Date().toISOString(),
        config: duplicatedConfig
      };
      setConfigs(prev => [...prev, newConfig]);
    } catch (error) {
      console.error('Error duplicating configuration:', error);
      setError(`Failed to duplicate configuration: ${error.message}`);
    } finally {
      setLoading(false);
    }
  };

  const handleExport = (config) => {
    // Create a clean config object for export (remove UI-specific fields)
    const exportConfig = {
      promptPath: config.config.promptPath,
      llm: config.config.llm,
      solution: config.config.solution,
      selection: config.config.selection,
      migration: config.config.migration,
      repository: config.config.repository,
      mapelites: config.config.mapelites,
      metrics: config.config.metrics
    };
    
    const yamlStr = yaml.dump(exportConfig, { 
      indent: 2,
      lineWidth: -1,
      noRefs: true,
      sortKeys: false
    });
    const dataBlob = new Blob([yamlStr], { type: 'application/x-yaml' });
    const url = URL.createObjectURL(dataBlob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${config.name.replace(/\s+/g, '_')}.yml`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  const handleImport = (event) => {
    const file = event.target.files[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = (e) => {
        try {
          let importedConfig;
          const content = e.target.result;
          
          // Try to parse as YAML first, then JSON as fallback
          try {
            importedConfig = yaml.load(content);
          } catch (yamlError) {
            try {
              importedConfig = JSON.parse(content);
            } catch (jsonError) {
              throw new Error('Invalid YAML or JSON format');
            }
          }
          
          const newConfig = {
            id: Date.now().toString(),
            name: file.name.replace(/\.(yml|yaml|json)$/, '') || 'Imported Config',
            created: new Date().toISOString(),
            modified: new Date().toISOString(),
            config: new OpenEvolveConfig(importedConfig)
          };
          
          // Send create message via WebSocket and wait for response
          sendWsRequest({
            type: 'CONFIG_CREATE',
            id: newConfig.id,
            config: newConfig.config
          }).then(() => {
            setConfigs([...configs, newConfig]);
          }).catch(error => {
            console.error('Error creating imported configuration:', error);
            alert(`Error creating imported configuration: ${error.message}`);
          });
        } catch (error) {
          alert(`Error importing configuration: ${error.message}`);
        }
      };
      reader.readAsText(file);
    }
    // Reset the input to allow importing the same file again
    event.target.value = '';
  };

  const handleCancel = () => {
    setViewMode('list');
    setIsEditing(false);
    setSelectedConfig(null);
  };

  return (
    <div className="col gap-4">
      {/* Inline toolbar specific to list/form */}
      <div className="row gap-3" style={{marginBottom:4}}>
        {viewMode === 'list' && (
          <>
            <button className="oe-btn primary" onClick={handleCreateNew}><Plus size={16}/> New</button>
            <label className="oe-btn outline" style={{cursor:'pointer'}}>
              <Upload size={16}/> Import
              <input type="file" accept=".yml,.yaml,.json" style={{display:'none'}} onChange={handleImport} />
            </label>
          </>
        )}
        {(viewMode === 'edit' || viewMode === 'create') && (
          <button className="oe-btn ghost" onClick={handleCancel}>Cancel</button>
        )}
      </div>

      {error && (
        <div className="oe-surface p-4" style={{borderColor:'var(--oe-danger)'}}>
          <div className="row justify-between align-center">
            <span style={{color:'var(--oe-danger)'}}>{error}</span>
            <button className="oe-btn ghost sm" onClick={()=>setError(null)}>Dismiss</button>
          </div>
        </div>
      )}
      {loading && (
        <div className="oe-surface p-4" style={{borderColor:'var(--oe-accent)'}}>
          <span className="text-dim">Working…</span>
        </div>
      )}

      <div className="oe-surface elevated p-5 animate-fade-in">
        {viewMode === 'list' && (
          <ConfigList
            configs={configs}
            onEdit={handleEdit}
            onDelete={handleDelete}
            onDuplicate={handleDuplicate}
            onExport={handleExport}
            disabled={loading}
          />
        )}
        {(viewMode === 'edit' || viewMode === 'create') && (
          <ConfigForm
            config={selectedConfig}
            mode={viewMode}
            onSave={handleSave}
            onCancel={handleCancel}
            disabled={loading}
          />
        )}
      </div>
    </div>
  );
};

export default ConfigManager;
