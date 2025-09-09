import React, { useState } from 'react';
import { Edit, Trash2, Copy, Download, Calendar, User } from 'lucide-react';

const ConfigList = ({ configs, onEdit, onDelete, onDuplicate, onExport }) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [sortBy, setSortBy] = useState('modified'); // 'name', 'created', 'modified'
  const [sortOrder, setSortOrder] = useState('desc'); // 'asc', 'desc'

  const filteredAndSortedConfigs = configs
    .filter(config => 
      config.name.toLowerCase().includes(searchTerm.toLowerCase())
    )
    .sort((a, b) => {
      let aValue = a[sortBy];
      let bValue = b[sortBy];
      
      if (sortBy === 'name') {
        aValue = aValue.toLowerCase();
        bValue = bValue.toLowerCase();
      }
      
      if (sortOrder === 'asc') {
        return aValue < bValue ? -1 : aValue > bValue ? 1 : 0;
      } else {
        return aValue > bValue ? -1 : aValue < bValue ? 1 : 0;
      }
    });

  const formatDate = (dateString) => {
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  const getConfigSummary = (config) => {
    const cfg = config.config;
    return {
      language: cfg.solution?.language || 'unknown',
      populationSize: cfg.repository?.populationSize || 0,
      iterations: cfg.mapelites?.numIterations || 0,
      islands: cfg.repository?.islands || 0
    };
  };

  if (configs.length === 0) {
    return (
      <div className="empty-state">
        <div className="empty-state-content">
          <h3>No configurations yet</h3>
          <p>Create your first OpenEvolve configuration to get started.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="config-list">
      <div className="list-controls">
        <div className="search-box">
          <input
            type="text"
            placeholder="Search configurations..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="search-input"
          />
        </div>
        
        <div className="sort-controls">
          <select 
            value={sortBy} 
            onChange={(e) => setSortBy(e.target.value)}
            className="sort-select"
          >
            <option value="modified">Last Modified</option>
            <option value="created">Date Created</option>
            <option value="name">Name</option>
          </select>
          
          <button
            onClick={() => setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc')}
            className="sort-order-btn"
            title={`Sort ${sortOrder === 'asc' ? 'Descending' : 'Ascending'}`}
          >
            {sortOrder === 'asc' ? '↑' : '↓'}
          </button>
        </div>
      </div>

      <div className="config-grid">
        {filteredAndSortedConfigs.map(config => {
          const summary = getConfigSummary(config);
          
          return (
            <div key={config.id} className="config-card">
              <div className="config-card-header">
                <h3 className="config-name">{config.name}</h3>
                <div className="config-actions">
                  <button
                    onClick={() => onEdit(config)}
                    className="action-btn edit"
                    title="Edit Configuration"
                  >
                    <Edit size={16} />
                  </button>
                  <button
                    onClick={() => onDuplicate(config)}
                    className="action-btn duplicate"
                    title="Duplicate Configuration"
                  >
                    <Copy size={16} />
                  </button>
                  <button
                    onClick={() => onExport(config)}
                    className="action-btn export"
                    title="Export Configuration"
                  >
                    <Download size={16} />
                  </button>
                  <button
                    onClick={() => onDelete(config.id)}
                    className="action-btn delete"
                    title="Delete Configuration"
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
              </div>
              
              <div className="config-summary">
                <div className="summary-row">
                  <span className="summary-label">Language:</span>
                  <span className="summary-value">{summary.language}</span>
                </div>
                <div className="summary-row">
                  <span className="summary-label">Population:</span>
                  <span className="summary-value">{summary.populationSize}</span>
                </div>
                <div className="summary-row">
                  <span className="summary-label">Iterations:</span>
                  <span className="summary-value">{summary.iterations}</span>
                </div>
                <div className="summary-row">
                  <span className="summary-label">Islands:</span>
                  <span className="summary-value">{summary.islands}</span>
                </div>
              </div>
              
              <div className="config-meta">
                <div className="meta-item">
                  <Calendar size={14} />
                  <span>Modified: {formatDate(config.modified)}</span>
                </div>
                <div className="meta-item">
                  <Calendar size={14} />
                  <span>Created: {formatDate(config.created)}</span>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default ConfigList;
