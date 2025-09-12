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
  const { solutions, setSolutions, statuses, setStatuses, evolutionEvents, sendWsRequest, fetchSolutions } = useContext(ConfigContext);
  const [evolutionType, setEvolutionType] = useState('restart');
  const [selectedSolutions, setSelectedSolutions] = useState([]);
  const [showCustomModal, setShowCustomModal] = useState(false);

  const configSolutions = solutions[config?.id] || [];
  const configEvents = evolutionEvents[config?.id] || [];
  const configStatus = statuses[config?.id] || 'NOT_RUNNING'; // Get status from backend
  const isRunning = configStatus === 'RUNNING';
  
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
      // Handle STARTED response here
      if (response && (response.type === 'STARTED' || response.status === 'RUNNING')) {
        setStatuses(prev => ({
          ...prev,
          [config.id]: 'RUNNING'
        }));
      }
      console.log('Evolution started response:', response);
    } catch (error) {
      console.error('Failed to start evolution:', error);
    }
  };

  const handleStopEvolution = async () => {
    try {
      const response = await sendWsRequest({
        type: 'STOP',
        id: config.id
      });
      // Handle STOPPED response here
      if (response && (response.type === 'STOPPED' || response.status === 'NOT_RUNNING')) {
        setStatuses(prev => ({
          ...prev,
          [config.id]: 'NOT_RUNNING'
        }));
      }
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
    // Check for error events in recent history for error state
    const hasRecentError = configEvents.length > 0 && 
      configEvents.slice(-5).some(event => event.type === 'ERROR');
    
    if (hasRecentError) {
      return <AlertCircle size={16} className="text-danger" />;
    }
    
    switch (configStatus) {
      case 'RUNNING': return <Activity size={16} className="spinning text-accent" />;
      case 'NOT_RUNNING': return <CheckCircle size={16} className="text-success" />;
      default: return <CheckCircle size={16} className="text-success" />;
    }
  };

  const getStatusText = () => {
    // Check for error events in recent history
    const hasRecentError = configEvents.length > 0 && 
      configEvents.slice(-5).some(event => event.type === 'ERROR');
    
    if (hasRecentError) {
      return 'Error';
    }
    
    switch (configStatus) {
      case 'RUNNING': return 'Running';
      case 'NOT_RUNNING': return 'Ready';
      default: return 'Ready';
    }
  };

  const formatEvolutionEvent = (event) => {
    const baseInfo = {
      timestamp: event.timestamp,
      level: 'info'
    };

    switch (event.type) {
      case 'SOLUTION_ADDED':
        return {
          ...baseInfo,
          message: `New solution added: ${event.solution?.id?.substring(0, 8) || 'unknown'} (Score: ${event.solution?.fitness?.score?.toFixed(3) || 'N/A'})`
        };
      case 'SOLUTION_REMOVED':
        return {
          ...baseInfo,
          message: `Solution removed: ${event.solution?.id?.substring(0, 8) || 'unknown'}`
        };
      case 'CELL_IMPROVED':
        return {
          ...baseInfo,
          level: 'success',
          message: `Cell improved! New: ${event.newSolution?.fitness?.score?.toFixed(3) || 'N/A'} (was: ${event.previousSolution?.fitness?.score?.toFixed(3) || 'N/A'}) - Iteration ${event.iteration || 'N/A'}`
        };
      case 'CELL_REJECTED':
        return {
          ...baseInfo,
          level: 'warn',
          message: `Cell rejected: ${event.candidateSolution?.fitness?.score?.toFixed(3) || 'N/A'} vs existing ${event.existingSolution?.fitness?.score?.toFixed(3) || 'N/A'} - Iteration ${event.iteration || 'N/A'}`
        };
      case 'NEW_BEST_SOLUTION':
        return {
          ...baseInfo,
          level: 'success',
          message: `🎉 NEW BEST SOLUTION! Score: ${event.newBest?.fitness?.score?.toFixed(3) || 'N/A'} (was: ${event.previousBest?.fitness?.score?.toFixed(3) || 'N/A'}) - Iteration ${event.iteration || 'N/A'}`
        };
      case 'ERROR':
        return {
          ...baseInfo,
          level: 'error',
          message: `Error in iteration ${event.iteration || 'N/A'}: ${event.exceptionMessage || 'Unknown error'} ${event.context ? `(${event.context})` : ''}`
        };
      case 'ITERATION_DONE':
        return {
          ...baseInfo,
          message: `Iteration ${event.iteration || 'N/A'} completed`
        };
      default:
        return {
          ...baseInfo,
          message: `Unknown event: ${event.type}`
        };
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
        {configEvents.length > 0 && (
          <div className="evolution-section">
            <h4>Evolution Log</h4>
            <div className="evolution-log">
              {configEvents.slice(-10).map((event, index) => {
                const formattedEvent = formatEvolutionEvent(event);
                return (
                  <div key={index} className={`log-entry ${formattedEvent.level}`}>
                    <span className="log-time">{formatDate(formattedEvent.timestamp)}</span>
                    <span className="log-message">{formattedEvent.message}</span>
                  </div>
                );
              })}
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
