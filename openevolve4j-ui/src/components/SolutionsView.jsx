import React from 'react';
import { FileCode, Play, Clock, TrendingUp, Database } from 'lucide-react';

const SolutionsView = ({ config }) => {
  return (
    <div className="solutions-view">
      <div className="view-header">
        <h3><FileCode size={20} /> Solutions</h3>
        <p className="text-dim">Manage and monitor solution evolution for this configuration</p>
      </div>

      <div className="oe-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))' }}>
        {/* Solution Status */}
        <div className="oe-surface p-4">
          <div className="row gap-3 mb-3">
            <div className="status-icon">
              <Play size={16} />
            </div>
            <div>
              <h4 className="mb-1">Evolution Status</h4>
              <span className="oe-badge">Not Running</span>
            </div>
          </div>
          <div className="status-details">
            <div className="status-item">
              <span className="text-dim">Current Generation:</span>
              <span>-</span>
            </div>
            <div className="status-item">
              <span className="text-dim">Best Score:</span>
              <span>-</span>
            </div>
            <div className="status-item">
              <span className="text-dim">Running Time:</span>
              <span>-</span>
            </div>
          </div>
          <div className="mt-4">
            <button className="oe-btn primary">
              <Play size={14} /> Start Evolution
            </button>
          </div>
        </div>

        {/* Recent Solutions */}
        <div className="oe-surface p-4">
          <div className="row gap-3 mb-3">
            <div className="status-icon">
              <TrendingUp size={16} />
            </div>
            <div>
              <h4 className="mb-1">Recent Solutions</h4>
              <span className="text-dim">Latest evolved solutions</span>
            </div>
          </div>
          <div className="solutions-list">
            <div className="empty-state text-center py-4">
              <div className="text-dim mb-2">No solutions yet</div>
              <div className="text-faint">Start evolution to see solutions here</div>
            </div>
          </div>
        </div>

        {/* Solution Analytics */}
        <div className="oe-surface p-4">
          <div className="row gap-3 mb-3">
            <div className="status-icon">
              <Database size={16} />
            </div>
            <div>
              <h4 className="mb-1">Analytics</h4>
              <span className="text-dim">Performance metrics</span>
            </div>
          </div>
          <div className="analytics-grid">
            <div className="metric-item">
              <div className="metric-value">0</div>
              <div className="metric-label">Total Solutions</div>
            </div>
            <div className="metric-item">
              <div className="metric-value">0</div>
              <div className="metric-label">Successful Runs</div>
            </div>
            <div className="metric-item">
              <div className="metric-value">-</div>
              <div className="metric-label">Avg. Score</div>
            </div>
            <div className="metric-item">
              <div className="metric-value">-</div>
              <div className="metric-label">Best Score</div>
            </div>
          </div>
        </div>

        {/* Configuration Info */}
        <div className="oe-surface p-4">
          <div className="row gap-3 mb-3">
            <div className="status-icon">
              <FileCode size={16} />
            </div>
            <div>
              <h4 className="mb-1">Solution Configuration</h4>
              <span className="text-dim">Current settings</span>
            </div>
          </div>
          <div className="config-details">
            <div className="config-item">
              <span className="text-dim">Language:</span>
              <span className="oe-badge accent">{config?.config?.solution?.language || 'Not set'}</span>
            </div>
            <div className="config-item">
              <span className="text-dim">Pattern:</span>
              <span className="mono text-dim">{config?.config?.solution?.pattern || 'Not set'}</span>
            </div>
            <div className="config-item">
              <span className="text-dim">Timeout:</span>
              <span>{config?.config?.solution?.evalTimeout || 'Not set'}</span>
            </div>
            <div className="config-item">
              <span className="text-dim">Full Rewrite:</span>
              <span className="oe-badge">{config?.config?.solution?.fullRewrite ? 'Yes' : 'No'}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SolutionsView;
