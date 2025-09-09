import React from 'react';
import { Plus, Minus } from 'lucide-react';

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
    
    const newMetrics = { ...metrics };
    const value = newMetrics[oldName];
    delete newMetrics[oldName];
    newMetrics[newName] = value;
    onChange(newMetrics);
  };

  const updateMetricValue = (metricName, value) => {
    onChange({
      ...metrics,
      [metricName]: value
    });
  };

  return (
    <div className="form-section">
      <h3>Metrics Configuration</h3>
      <p className="field-help">Define metrics to optimize. Each metric can be set to maximize (true) or minimize (false).</p>
      
      <div className="metrics-container">
        {Object.entries(metrics).map(([metricName, isMaximize]) => (
          <div key={metricName} className="metric-row">
            <div className="metric-name-input">
              <label>Metric Name</label>
              <input
                type="text"
                value={metricName}
                onChange={(e) => updateMetricName(metricName, e.target.value)}
                placeholder="Enter metric name"
              />
            </div>
            
            <div className="metric-objective">
              <label>Objective</label>
              <select
                value={isMaximize ? 'maximize' : 'minimize'}
                onChange={(e) => updateMetricValue(metricName, e.target.value === 'maximize')}
              >
                <option value="maximize">Maximize</option>
                <option value="minimize">Minimize</option>
              </select>
            </div>
            
            <button
              type="button"
              onClick={() => removeMetric(metricName)}
              className="btn-remove"
              title="Remove metric"
            >
              <Minus size={16} />
            </button>
          </div>
        ))}
        
        <button
          type="button"
          onClick={addMetric}
          className="btn-add"
        >
          <Plus size={16} />
          Add Metric
        </button>
      </div>
    </div>
  );
};

export default MetricsSection;
