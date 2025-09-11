import React, { useState, useContext, useEffect } from 'react';
import { 
  Play, 
  RotateCcw, 
  Settings, 
  Target,
  CheckCircle,
  AlertCircle,
  RefreshCw,
  Database,
  Clock,
  TrendingUp,
  Activity,
  ChevronDown,
  X
} from 'lucide-react';
import { ConfigContext } from '../App';

const EvolutionView = ({ config }) => {
  const { solutions, setSolutions, sendWsRequest } = useContext(ConfigContext);
  const [evolutionType, setEvolutionType] = useState('restart');
  const [selectedSolutions, setSelectedSolutions] = useState([]);
  const [isRunning, setIsRunning] = useState(false);
  const [status, setStatus] = useState('idle'); // idle | running | paused | error
  const [logs, setLogs] = useState([]);
  const [showCustomModal, setShowCustomModal] = useState(false);

  const configSolutions = solutions[config?.id] || [];
  
  // Filter solutions that can be used as initial solutions (have valid files)
  const viableSolutions = configSolutions.filter(solution => 
    solution.solution?.files && Object.keys(solution.solution.files).length > 0
  );

  // Evolution mode options
  const evolutionModes = [
    {
      value: 'restart',
      label: 'Restart',
      icon: RotateCcw,
      description: 'Start evolution from scratch with random population',
      disabled: false
    },
    {
      value: 'continue',
      label: 'Continue',
      icon: Play,
      description: 'Resume evolution from the last checkpoint',
      disabled: configSolutions.length === 0
    },
    {
      value: 'custom',
      label: 'Custom',
      icon: Settings,
      description: 'Start with selected solutions as initial population',
      disabled: viableSolutions.length === 0
    }
  ];

  // Fetch solutions when component mounts or config changes
  useEffect(() => {
    if (config?.id) {
      fetchSolutions();
    }
  }, [config?.id]);

  const fetchSolutions = async () => {
    if (!config?.id) return;
    
    try {
      const response = await sendWsRequest({
        type: 'GET_SOLUTIONS',
        id: config.id
      });
      
      if (response.id && response.solutions) {
        setSolutions(prev => ({
          ...prev,
          [response.id]: response.solutions
        }));
      }
    } catch (error) {
      console.error('Failed to fetch solutions:', error);
    }
  };

  const handleSolutionToggle = (solutionId) => {
    setSelectedSolutions(prev => 
      prev.includes(solutionId) 
        ? prev.filter(id => id !== solutionId)
        : [...prev, solutionId]
    );
  };

  const handleModeChange = (mode) => {
    setEvolutionType(mode);
    // Reset selected solutions when changing modes
    if (mode !== 'custom') {
      setSelectedSolutions([]);
    }
  };

  const handleCustomModalConfirm = () => {
    setShowCustomModal(false);
  };

  const handleCustomModalCancel = () => {
    setShowCustomModal(false);
    setEvolutionType('restart'); // Reset to restart if user cancels
    setSelectedSolutions([]);
  };

  const handleRunEvolution = async () => {
    setIsRunning(true);
    setStatus('running');
    
    try {
      // Build the event payload to match Java backend structure
      const eventPayload = {
        type: 'START',
        id: config.id,
        restart: evolutionType === 'restart',
        ...(evolutionType === 'custom' && selectedSolutions.length > 0 && { 
          initialSolutions: selectedSolutions 
        })
      };

      console.log('Starting evolution with payload:', eventPayload);
      
      const response = await sendWsRequest(eventPayload);
      
      // Add a log entry
      setLogs(prev => [...prev, {
        timestamp: new Date().toISOString(),
        level: 'info',
        message: `Evolution started with type: ${evolutionType}`
      }]);

    } catch (error) {
      console.error('Failed to start evolution:', error);
      setStatus('error');
      setLogs(prev => [...prev, {
        timestamp: new Date().toISOString(),
        level: 'error',
        message: `Failed to start evolution: ${error.message}`
      }]);
    } finally {
      // For demo purposes, reset after 2 seconds
      setTimeout(() => {
        setIsRunning(false);
        setStatus('idle');
      }, 2000);
    }
  };

  const handleStopEvolution = async () => {
    try {
      await sendWsRequest({
        type: 'STOP',
        id: config.id
      });
      
      setStatus('idle');
      setIsRunning(false);
      
      setLogs(prev => [...prev, {
        timestamp: new Date().toISOString(),
        level: 'info',
        message: 'Evolution stopped by user'
      }]);
    } catch (error) {
      console.error('Failed to stop evolution:', error);
    }
  };

  const formatDate = (dateString) => {
    try {
      return new Date(dateString).toLocaleTimeString();
    } catch {
      return dateString;
    }
  };

  const getStatusIcon = () => {
    switch (status) {
      case 'running': return <Activity size={16} className="spinning text-accent" />;
      case 'error': return <AlertCircle size={16} className="text-danger" />;
      case 'paused': return <Clock size={16} className="text-warn" />;
      default: return <CheckCircle size={16} className="text-success" />;
    }
  };

  const getStatusText = () => {
    switch (status) {
      case 'running': return 'Running';
      case 'error': return 'Error';
      case 'paused': return 'Paused';
      default: return 'Ready';
    }
  };

  return (
    <div className="evolution-view">
      <div className="evolution-header">
        <div className="header-content">
          <div className="header-info">
            <h3><Target size={20} /> Evolution</h3>
            <p className="text-dim">
              Start and monitor evolution process for <span className="text-accent">{config?.name}</span>
            </p>
          </div>
          <div className="header-status">
            <div className="status-indicator">
              {getStatusIcon()}
              <span>Status: {getStatusText()}</span>
            </div>
          </div>
        </div>

        {/* Quick Stats */}
        <div className="evolution-stats">
          <div className="stat-item">
            <div className="stat-value">{configSolutions.length}</div>
            <div className="stat-label">Total Solutions</div>
          </div>
          <div className="stat-item">
            <div className="stat-value">{viableSolutions.length}</div>
            <div className="stat-label">Viable Solutions</div>
          </div>
          <div className="stat-item">
            <div className="stat-value">
              {configSolutions.length > 0 ? Math.max(...configSolutions.map(s => s.iteration || 0)) : 0}
            </div>
            <div className="stat-label">Current Iteration</div>
          </div>
          <div className="stat-item">
            <div className="stat-value">
              {configSolutions.length > 0 
                ? Math.max(...configSolutions.map(s => s.fitness?.score || 0)).toFixed(3)
                : '-'
              }
            </div>
            <div className="stat-label">Best Score</div>
          </div>
        </div>
      </div>

      <div className="evolution-content">
        {/* Evolution Type Selection */}
        <div className="evolution-section">
          <h4>Evolution Mode</h4>
          <div className="evolution-mode-selector">
            <div className="mode-dropdown">
              <label htmlFor="evolution-mode">Select evolution mode:</label>
              <select 
                id="evolution-mode"
                value={evolutionType}
                onChange={(e) => handleModeChange(e.target.value)}
                disabled={isRunning}
                className="oe-select"
              >
                {evolutionModes.map(mode => (
                  <option 
                    key={mode.value} 
                    value={mode.value} 
                    disabled={mode.disabled}
                  >
                    {mode.label} - {mode.description}
                  </option>
                ))}
              </select>
            </div>
            
            {/* Show selected mode info */}
            {(() => {
              const selectedMode = evolutionModes.find(m => m.value === evolutionType);
              const IconComponent = selectedMode?.icon;
              return selectedMode && (
                <div className="selected-mode-info">
                  <div className="mode-display">
                    <IconComponent size={18} />
                    <div>
                      <h5>{selectedMode.label}</h5>
                      <p>{selectedMode.description}</p>
                      {evolutionType === 'custom' && (
                        <div className="custom-mode-controls">
                          <button 
                            className="oe-btn outline sm"
                            onClick={() => setShowCustomModal(true)}
                            disabled={viableSolutions.length === 0}
                          >
                            <Settings size={14} />
                            Select Solutions
                          </button>
                          {selectedSolutions.length > 0 && (
                            <span className="custom-selection-count">
                              {selectedSolutions.length} solution{selectedSolutions.length !== 1 ? 's' : ''} selected
                            </span>
                          )}
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              );
            })()}
          </div>
        </div>

        {/* Controls */}
        <div className="evolution-section">
          <h4>Controls</h4>
          <div className="evolution-controls">
            {!isRunning ? (
              <button 
                className="oe-btn primary"
                onClick={handleRunEvolution}
                disabled={
                  (evolutionType === 'continue' && configSolutions.length === 0) ||
                  (evolutionType === 'custom' && (viableSolutions.length === 0 || selectedSolutions.length === 0))
                }
              >
                <Play size={16} />
                Run Evolution
              </button>
            ) : (
              <button 
                className="oe-btn outline"
                onClick={handleStopEvolution}
              >
                <RefreshCw size={16} />
                Stop Evolution
              </button>
            )}
          </div>
          
          {/* Validation Messages */}
          {evolutionType === 'continue' && configSolutions.length === 0 && (
            <div className="validation-message warn">
              <AlertCircle size={16} />
              No existing solutions found. Use Restart mode to begin evolution.
            </div>
          )}
          
          {evolutionType === 'custom' && viableSolutions.length === 0 && (
            <div className="validation-message warn">
              <AlertCircle size={16} />
              No viable solutions available. Solutions need valid files to be used as initial population.
            </div>
          )}
          
          {evolutionType === 'custom' && viableSolutions.length > 0 && selectedSolutions.length === 0 && (
            <div className="validation-message info">
              <AlertCircle size={16} />
              Click "Select Solutions" to choose which solutions to use as initial population.
            </div>
          )}
        </div>

        {/* Evolution Log */}
        {logs.length > 0 && (
          <div className="evolution-section">
            <h4>Evolution Log</h4>
            <div className="evolution-log">
              {logs.slice(-10).map((log, index) => (
                <div key={index} className={`log-entry ${log.level}`}>
                  <span className="log-time">{formatDate(log.timestamp)}</span>
                  <span className="log-message">{log.message}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Custom Solution Selection Modal */}
      {showCustomModal && (
        <div className="modal-overlay" onClick={handleCustomModalCancel}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h4>Select Initial Solutions</h4>
              <button 
                className="oe-btn ghost sm"
                onClick={handleCustomModalCancel}
              >
                <X size={16} />
              </button>
            </div>
            
            <div className="modal-body">
              <p className="text-dim">
                Choose which solutions to use as the initial population for custom evolution. 
                Selected solutions will be used to seed the new generation.
              </p>
              
              <div className="modal-stats">
                <div className="stat-item sm">
                  <span className="stat-value">{viableSolutions.length}</span>
                  <span className="stat-label">Available Solutions</span>
                </div>
                <div className="stat-item sm">
                  <span className="stat-value">{selectedSolutions.length}</span>
                  <span className="stat-label">Selected</span>
                </div>
              </div>

              <div className="solution-selection-modal">
                {viableSolutions.map(solution => (
                  <label key={solution.id} className="solution-option-modal">
                    <input
                      type="checkbox"
                      checked={selectedSolutions.includes(solution.id)}
                      onChange={() => handleSolutionToggle(solution.id)}
                    />
                    <div className="solution-content-modal">
                      <div className="solution-header">
                        <code className="solution-id">{solution.id?.substring(0, 8)}</code>
                        <span className="solution-iteration">Iteration {solution.iteration || 0}</span>
                        <span className="solution-score">
                          Score: {solution.fitness?.score?.toFixed(3) || '-'}
                        </span>
                      </div>
                    </div>
                  </label>
                ))}
              </div>
            </div>
            
            <div className="modal-footer">
              <button 
                className="oe-btn outline"
                onClick={handleCustomModalCancel}
              >
                Cancel
              </button>
              <button 
                className="oe-btn primary"
                onClick={handleCustomModalConfirm}
                disabled={selectedSolutions.length === 0}
              >
                Confirm Selection ({selectedSolutions.length})
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default EvolutionView;
