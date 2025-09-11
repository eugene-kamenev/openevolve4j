import React from 'react';
import { Save, Clock, Download, Trash2, RefreshCw, Archive } from 'lucide-react';

const CheckpointsView = ({ config }) => {
  return (
    <div className="checkpoints-view">
      <div className="view-header">
        <h3><Archive size={20} /> Checkpoints</h3>
        <p className="text-dim">Manage evolution checkpoints and restore points for this configuration</p>
      </div>

      <div className="checkpoints-actions mb-4">
        <div className="row justify-between">
          <div className="row gap-3">
            <button className="oe-btn primary">
              <Save size={14} /> Create Checkpoint
            </button>
            <button className="oe-btn outline">
              <RefreshCw size={14} /> Refresh
            </button>
          </div>
          <div className="checkpoint-settings">
            <span className="text-dim">Auto-save every:</span>
            <span className="oe-badge accent">{config?.config?.repository?.checkpointInterval || 10} generations</span>
          </div>
        </div>
      </div>

      <div className="oe-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(350px, 1fr))' }}>
        {/* Checkpoint Stats */}
        <div className="oe-surface p-4">
          <div className="row gap-3 mb-3">
            <div className="status-icon">
              <Archive size={16} />
            </div>
            <div>
              <h4 className="mb-1">Checkpoint Statistics</h4>
              <span className="text-dim">Storage and performance</span>
            </div>
          </div>
          <div className="stats-grid">
            <div className="stat-item">
              <div className="stat-value">0</div>
              <div className="stat-label">Total Checkpoints</div>
            </div>
            <div className="stat-item">
              <div className="stat-value">0 MB</div>
              <div className="stat-label">Storage Used</div>
            </div>
            <div className="stat-item">
              <div className="stat-value">-</div>
              <div className="stat-label">Latest Checkpoint</div>
            </div>
            <div className="stat-item">
              <div className="stat-value">Auto</div>
              <div className="stat-label">Cleanup Policy</div>
            </div>
          </div>
        </div>

        {/* Repository Settings */}
        <div className="oe-surface p-4">
          <div className="row gap-3 mb-3">
            <div className="status-icon">
              <Clock size={16} />
            </div>
            <div>
              <h4 className="mb-1">Repository Settings</h4>
              <span className="text-dim">Current configuration</span>
            </div>
          </div>
          <div className="settings-details">
            <div className="setting-item">
              <span className="text-dim">Population Size:</span>
              <span className="oe-badge">{config?.config?.repository?.populationSize || 50}</span>
            </div>
            <div className="setting-item">
              <span className="text-dim">Archive Size:</span>
              <span className="oe-badge">{config?.config?.repository?.archiveSize || 10}</span>
            </div>
            <div className="setting-item">
              <span className="text-dim">Islands:</span>
              <span className="oe-badge">{config?.config?.repository?.islands || 2}</span>
            </div>
            <div className="setting-item">
              <span className="text-dim">Checkpoint Interval:</span>
              <span className="oe-badge accent">{config?.config?.repository?.checkpointInterval || 10}</span>
            </div>
          </div>
        </div>
      </div>

      {/* Checkpoints List */}
      <div className="oe-surface p-4 mt-4">
        <div className="row justify-between align-center mb-4">
          <h4 className="mb-0">Available Checkpoints</h4>
          <div className="row gap-2">
            <button className="oe-btn ghost sm">
              <Download size={12} /> Export All
            </button>
            <button className="oe-btn ghost sm danger">
              <Trash2 size={12} /> Cleanup Old
            </button>
          </div>
        </div>

        <div className="checkpoints-table">
          <div className="table-header">
            <div className="table-row">
              <div className="table-cell">Name</div>
              <div className="table-cell">Generation</div>
              <div className="table-cell">Created</div>
              <div className="table-cell">Size</div>
              <div className="table-cell">Best Score</div>
              <div className="table-cell">Actions</div>
            </div>
          </div>
          <div className="table-body">
            <div className="empty-state text-center py-8">
              <Archive size={32} className="text-faint mb-3" />
              <div className="text-dim mb-2">No checkpoints available</div>
              <div className="text-faint">Checkpoints will appear here as evolution progresses</div>
              <button className="oe-btn primary mt-3">
                <Save size={14} /> Create First Checkpoint
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default CheckpointsView;
