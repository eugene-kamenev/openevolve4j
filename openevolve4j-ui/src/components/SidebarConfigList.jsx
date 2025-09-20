import React, { useState, useContext } from 'react';
import { Plus, Upload, Search, Trash2, Copy, Download } from 'lucide-react';
import { ConfigContext } from '../ConfigContext';
import yaml from 'js-yaml';
import { createConfig } from '../Entity';
import { ProblemsApi } from '../services/api';

const SidebarConfigList = ({ selectedConfigId, onSelectConfig, onCreateNew }) => {
  const { configs, setConfigs } = useContext(ConfigContext);
  const [searchTerm, setSearchTerm] = useState('');
  const [loading, setLoading] = useState(false);

  const filteredConfigs = configs.filter(config => 
    (config.name || '').toLowerCase().includes(searchTerm.toLowerCase())
  );

  const handleDelete = async (configId, event) => {
    event.stopPropagation();
    if (window.confirm('Are you sure you want to delete this configuration?')) {
      setLoading(true);
      try {
        await ProblemsApi.remove(configId);
        setConfigs(prev => prev.filter(c => c.id !== configId));
      } catch (error) {
        console.error('Error deleting configuration:', error);
        alert(`Failed to delete configuration: ${error.message}`);
      } finally {
        setLoading(false);
      }
    }
  };

  const handleDuplicate = async (config, event) => {
    event.stopPropagation();
    setLoading(true);
    try {
      const duplicatedConfig = {
        name: `${config.name} (Copy)`,
        config: createConfig(config.config)
      };
      const created = await ProblemsApi.create(duplicatedConfig);
      setConfigs(prev => [...prev, created]);
    } catch (error) {
      console.error('Error duplicating configuration:', error);
      alert(`Failed to duplicate configuration: ${error.message}`);
    } finally {
      setLoading(false);
    }
  };

  const handleExport = (config, event) => {
    event.stopPropagation();
    const exportConfig = {
      type: config.config.type || 'MAPELITES',
      promptPath: config.config.promptPath,
      llm: config.config.llm,
      solution: config.config.solution,
      // MAPELITES fields
      selection: config.config.selection,
      migration: config.config.migration,
      repository: config.config.repository,
      mapelites: config.config.mapelites,
      // TREE fields
      llmGroups: config.config.llmGroups,
      iterations: config.config.iterations,
      explorationConstant: config.config.explorationConstant,
      metrics: config.config.metrics
    };
    const yamlStr = yaml.dump(exportConfig, { indent: 2, lineWidth: -1, noRefs: true, sortKeys: false });
    const dataBlob = new Blob([yamlStr], { type: 'application/x-yaml' });
    const url = URL.createObjectURL(dataBlob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${(config.name || 'config').replace(/\s+/g, '_')}.yml`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  const handleImport = (event) => {
    const file = event.target.files[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = async (e) => {
        try {
          let importedConfig;
          const content = e.target.result;
          try {
            importedConfig = yaml.load(content);
          } catch {
            try { importedConfig = JSON.parse(content); } catch { throw new Error('Invalid YAML or JSON format'); }
          }
          const body = {
            name: file.name.replace(/\.(yml|yaml|json)$/, '') || 'Imported Config',
            config: createConfig(importedConfig)
          };
          const created = await ProblemsApi.create(body);
          setConfigs(prev => [...prev, created]);
        } catch (error) {
          alert(`Error importing configuration: ${error.message}`);
        }
      };
      reader.readAsText(file);
    }
    event.target.value = '';
  };

  return (
    <div className="sidebar-config-list">
      {/* Header with actions */}
      <div className="sidebar-header">
        <div className="sidebar-actions">
          <button 
            className="oe-btn primary sm" 
            onClick={onCreateNew}
            disabled={loading}
          >
            <Plus size={14}/> New
          </button>
          <label className="oe-btn outline sm" style={{cursor:'pointer'}}>
            <Upload size={14}/> Import
            <input 
              type="file" 
              accept=".yml,.yaml,.json" 
              style={{display:'none'}} 
              onChange={handleImport}
              disabled={loading}
            />
          </label>
        </div>
      </div>

      {/* Search */}
      <div className="sidebar-search">
        <div className="search-input-wrapper">
          <Search size={14} className="search-icon" />
          <input
            type="text"
            placeholder="Search configs..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="search-input"
          />
        </div>
      </div>

      {/* Config List */}
      <div className="config-list">
        {filteredConfigs.length === 0 ? (
          <div className="empty-state">
            {configs.length === 0 ? (
              <div className="empty-content">
                <p>No configurations yet</p>
                <button className="oe-btn primary sm" onClick={onCreateNew}>
                  Create First Config
                </button>
              </div>
            ) : (
              <div className="empty-content">
                <p>No configs match your search</p>
              </div>
            )}
          </div>
        ) : (
          filteredConfigs.map(config => (
            <div 
              key={config.id} 
              className={`config-item ${selectedConfigId === config.id ? 'selected' : ''}`}
              onClick={() => onSelectConfig(config)}
            >
              <div className="config-item-header">
                <h4 className="config-name">{config.name}</h4>
              </div>
              <div className="config-item-actions">
                <button
                  className="action-btn"
                  onClick={(e) => handleDuplicate(config, e)}
                  title="Duplicate"
                  disabled={loading}
                >
                  <Copy size={12} />
                </button>
                <button
                  className="action-btn"
                  onClick={(e) => handleExport(config, e)}
                  title="Export"
                >
                  <Download size={12} />
                </button>
                <button
                  className="action-btn delete"
                  onClick={(e) => handleDelete(config.id, e)}
                  title="Delete"
                  disabled={loading}
                >
                  <Trash2 size={12} />
                </button>
              </div>
            </div>
          ))
        )}
      </div>

      {loading && (
        <div className="sidebar-loading">
          <span className="text-dim">Working...</span>
        </div>
      )}
    </div>
  );
};

export default SidebarConfigList;
