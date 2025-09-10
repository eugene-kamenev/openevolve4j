import React from 'react';
import { Plus, Minus, Target } from 'lucide-react';

const MetricsSection = ({ metrics = {}, onChange }) => {
  const addMetric = () => {
    const metricName = `metric${Object.keys(metrics).length + 1}`;
    onChange({
      ...metrics,
      [metricName]: true
    });
  };

  const removeMetric = (metricName) => {
    const newMetrics = { ...metrics };
    delete newMetrics[metricName];
    onChange(newMetrics);
  };

  const updateMetricName = (oldName, newName) => {
    if (oldName === newName) return;
    
    // Validate metric name (basic validation)
    if (!newName.trim()) return;
    
    const newMetrics = { ...metrics };
    const value = newMetrics[oldName];
    delete newMetrics[oldName];
    newMetrics[newName.trim()] = value;
    onChange(newMetrics);
  };

  const updateMetricValue = (metricName, value) => {
    onChange({
      ...metrics,
      [metricName]: value
    });
  };

  const hasMetrics = Object.keys(metrics).length > 0;

  return (
    <div className="form-section">
      <h3>
        <Target size={20} />
        Metrics Configuration
      </h3>
      <p className="field-help">
        Define metrics to optimize during evolution. Each metric can be set to maximize (improve by increasing) 
        or minimize (improve by decreasing). Common metrics include accuracy, performance, complexity, etc.
      </p>
      
      <div className="metrics-list">
        <div className="metrics-header">
          <span>Metric Name</span>
          <span>Optimization Goal</span>
          <span>Actions</span>
        </div>
        <div className="metrics-container">
          {Object.entries(metrics).map(([metricName, isMaximize], index) => (
            <div key={metricName} className="metric-row" style={{ animationDelay: `${index * 0.1}s` }}>
              <div className="metric-name-input">
                <input
                  type="text"
                  value={metricName}
                  onChange={(e) => updateMetricName(metricName, e.target.value)}
                  placeholder="Enter metric name (e.g., accuracy, speed)"
                  aria-label={`Metric name: ${metricName}`}
                  maxLength={50}
                />
              </div>
              
              <div className="metric-objective">
                <select
                  value={isMaximize ? 'maximize' : 'minimize'}
                  onChange={(e) => updateMetricValue(metricName, e.target.value === 'maximize')}
                  aria-label={`Optimization goal for ${metricName}`}
                >
                  <option value="maximize">📈 Maximize (Higher is better)</option>
                  <option value="minimize">📉 Minimize (Lower is better)</option>
                </select>
              </div>
              
              <button
                type="button"
                onClick={() => removeMetric(metricName)}
                className="btn-remove"
                title={`Remove ${metricName} metric`}
                aria-label={`Remove ${metricName} metric`}
              >
                <Minus size={16} />
              </button>
            </div>
          ))}
        </div>
        
        <button
          type="button"
          onClick={addMetric}
          className="btn-add-metric"
          aria-label="Add new metric"
        >
          <Plus size={16} />
          Add New Metric
        </button>
      </div>
      
      {hasMetrics && (
        <div className="metrics-summary">
          <small className="field-help">
            <strong>{Object.keys(metrics).length}</strong> metric{Object.keys(metrics).length !== 1 ? 's' : ''} defined. 
            {' '}The evolution algorithm will optimize solutions based on these metrics.
          </small>
        </div>
      )}
    </div>
  );
};

export default MetricsSection;
