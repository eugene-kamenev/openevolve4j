import React, { useState, useContext } from 'react';
import { Plus, Upload, Search, Edit, Trash2, Copy, Download } from 'lucide-react';
import { ConfigContext } from '../App';
import yaml from 'js-yaml';
import { OpenEvolveConfig } from '../OpenEvolveConfig';

const SidebarConfigList = ({ selectedConfigId, onSelectConfig, onCreateNew }) => {
  const { configs, setConfigs, sendWsRequest } = useContext(ConfigContext);
  const [searchTerm, setSearchTerm] = useState('');
  const [loading, setLoading] = useState(false);

  const filteredConfigs = configs.filter(config => 
    config.name.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const handleDelete = async (configId, event) => {
    event.stopPropagation();
    if (window.confirm('Are you sure you want to delete this configuration?')) {
      setLoading(true);
      try {
        await sendWsRequest({
          type: 'CONFIG_DELETE',
          id: configId
        });
        setConfigs(configs.filter(c => c.id !== configId));
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
      const configId = Date.now().toString();
      const duplicatedConfig = {
        ...config.config,
        name: `${config.name} (Copy)`
      };
      
      await sendWsRequest({
        type: 'CONFIG_CREATE',
        id: configId,
        config: duplicatedConfig
      });
      
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
      alert(`Failed to duplicate configuration: ${error.message}`);
    } finally {
      setLoading(false);
    }
  };

  const handleExport = (config, event) => {
    event.stopPropagation();
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
    event.target.value = '';
  };

  const formatDate = (dateString) => {
    return new Date(dateString).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
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
              <div className="config-item-meta">
                <div className="config-info">
                  <span className="config-language">
                    {config.config.solution?.language || 'unknown'}
                  </span>
                  <span className="config-population">
                    Pop: {config.config.repository?.populationSize || 0}
                  </span>
                </div>
                <div className="config-date">
                  {formatDate(config.modified)}
                </div>
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
