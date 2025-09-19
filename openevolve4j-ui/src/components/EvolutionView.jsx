import React, { useState, useContext, useEffect } from 'react';
import { 
  Play, 
  RotateCcw, 
  Settings, 
  Target,
  CheckCircle,
  AlertCircle,
  RefreshCw,
  Clock,
  TrendingUp,
  Activity,
  X
} from 'lucide-react';
import { ConfigContext } from '../ConfigContext';
import { formatScore } from '../utils/formatters';
import { RunsApi } from '../services/api';
import SolutionsBrowser from './SolutionsBrowser';

const EvolutionView = ({ config }) => {
  const { solutions, statuses, setStatuses, evolutionEvents, fetchSolutions, activeRuns, setActiveRuns } = useContext(ConfigContext);
  const configSolutions = solutions[config?.id] || [];
  const [evolutionType, setEvolutionType] = useState(configSolutions.length > 0 ? 'continue' : 'restart');
  const [selectedSolutions, setSelectedSolutions] = useState([]);
  const [showCustomModal, setShowCustomModal] = useState(false);
  const [currentIteration, setCurrentIteration] = useState(0);
  const configEvents = evolutionEvents[config?.id] || [];
  const configStatus = statuses[config?.id] || 'NOT_RUNNING';
  const isRunning = configStatus === 'RUNNING';
  const currentRunId = activeRuns[config?.id];

  // Poll backend /run/status and sync running state for this problem
  useEffect(() => {
    const problemId = config?.id;
    if (!problemId) return;
    let cancelled = false;
    let timer;

    const poll = async () => {
      try {
        const statusMap = await RunsApi.status();
        const knownRunId = activeRuns[problemId];
        if (knownRunId && statusMap?.[knownRunId] === 'RUNNING') {
          if (!cancelled) {
            setStatuses(prev => ({ ...prev, [problemId]: 'RUNNING' }));
          }
          return;
        }

        // Discover if any running run belongs to this problem
        const entries = Object.entries(statusMap || {});
        let foundRunId = null;
        for (const [runId, state] of entries) {
          if (state !== 'RUNNING') continue;
          try {
            const run = await RunsApi.get(runId);
            if (run?.problemId === problemId) {
              foundRunId = runId;
              break;
            }
          } catch (_) {
            // ignore individual lookup errors
          }
        }

        if (!cancelled) {
          if (foundRunId) {
            setStatuses(prev => ({ ...prev, [problemId]: 'RUNNING' }));
            if (activeRuns[problemId] !== foundRunId) {
              setActiveRuns(prev => ({ ...prev, [problemId]: foundRunId }));
            }
          } else {
            setStatuses(prev => ({ ...prev, [problemId]: 'NOT_RUNNING' }));
            // Do not clear activeRuns here; keep latest for potential continue
          }
        }
      } catch (_) {
        // network or backend error; skip without changing UI state
      }
    };

    // initial tick and interval
    poll();
    timer = setInterval(poll, 3000);
    return () => {
      cancelled = true;
      if (timer) clearInterval(timer);
    };
  }, [config?.id, activeRuns, setActiveRuns, setStatuses]);
  
  // Update current iteration from ITERATION_DONE events
  useEffect(() => {
    if (configEvents.length > 0) {
      const lastIterationEvent = configEvents
        .filter(event => event.type === 'ITERATION_DONE')
        .slice(-1)[0];
      
      if (lastIterationEvent && lastIterationEvent.iteration !== undefined) {
        setCurrentIteration(lastIterationEvent.iteration);
      }
    }
  }, [configEvents]);
  
  // Reset iteration when evolution starts
  useEffect(() => {
    if (isRunning) {
      setCurrentIteration(0);
    }
  }, [isRunning]);
  
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
      disabled: !currentRunId
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
      const payload = { problemId: config.id };
      if (evolutionType === 'custom' && selectedSolutions.length > 0) payload.solutionIds = selectedSolutions;
      if (evolutionType === 'continue' && currentRunId) payload.runId = currentRunId;
      const resp = await RunsApi.start(payload);
      if (resp?.id) {
        setActiveRuns(prev => ({ ...prev, [config.id]: resp.id }));
        setStatuses(prev => ({ ...prev, [config.id]: 'RUNNING' }));
        fetchSolutions(config.id);
      }
    } catch (e) {
      console.error('Failed to start evolution:', e);
      alert(`Failed to start evolution: ${e.message}`);
    }
  };

  const handleStopEvolution = async () => {
    try {
      const runId = currentRunId;
      if (!runId) {
        setStatuses(prev => ({ ...prev, [config.id]: 'NOT_RUNNING' }));
        return;
      }
      await RunsApi.stop(runId);
      setStatuses(prev => ({ ...prev, [config.id]: 'NOT_RUNNING' }));
      setActiveRuns(prev => ({ ...prev, [config.id]: undefined }));
      // Optionally refresh solutions
      fetchSolutions(config.id);
    } catch (e) {
      console.error('Failed to stop evolution:', e);
      alert(`Failed to stop evolution: ${e.message}`);
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
          message: `New solution added: ${event.solution?.id?.substring(0, 8) || 'unknown'} (fitness: ${formatScore(event.solution?.fitness, config?.config?.metrics) || 'N/A'})`
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
          message: `Cell improved! New: ${formatScore(event.newSolution?.fitness, config?.config?.metrics) || 'N/A'} (was: ${formatScore(event.previousSolution?.fitness, config?.config?.metrics) || 'N/A'}) - Iteration ${event.iteration || 'N/A'}`
        };
      case 'CELL_REJECTED':
        return {
          ...baseInfo,
          level: 'warn',
          message: `Cell rejected: ${formatScore(event.candidateSolution?.fitness, config?.config?.metrics) || 'N/A'} vs existing ${formatScore(event.existingSolution?.fitness, config?.config?.metrics) || 'N/A'} - Iteration ${event.iteration || 'N/A'}`
        };
      case 'NEW_BEST_SOLUTION':
        return {
          ...baseInfo,
          level: 'success',
          message: `🎉 NEW BEST SOLUTION! Fitness: ${formatScore(event.newBest?.fitness, config?.config?.metrics) || 'N/A'} (was: ${formatScore(event.previousBest?.fitness, config?.config?.metrics) || 'N/A'}) - Iteration ${event.iteration || 'N/A'}`
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
                  (evolutionType === 'continue' && !currentRunId) ||
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

          {/* Progress Bar */}
          {config?.config?.mapelites?.numIterations && (
            <div className="evolution-progress">
              <div className="progress-header">
                <div className="progress-info">
                  <TrendingUp size={16} />
                  <span>Progress</span>
                </div>
                <div className="progress-stats">
                  <span className="current-iteration">{currentIteration}</span>
                  <span className="separator">/</span>
                  <span className="total-iterations">{config.config.mapelites.numIterations}</span>
                  <span className="progress-percentage">
                    ({Math.round((currentIteration / config.config.mapelites.numIterations) * 100)}%)
                  </span>
                </div>
              </div>
              <div className="progress-bar-container">
                <div 
                  className="progress-bar-fill"
                  style={{
                    width: `${Math.min((currentIteration / config.config.mapelites.numIterations) * 100, 100)}%`,
                    backgroundColor: isRunning ? '#22c55e' : '#94a3b8',
                    transition: 'width 0.3s ease-in-out'
                  }}
                />
              </div>
              <div className="progress-time">
                <Clock size={14} />
                <span>
                  {isRunning ? 'Running...' : 
                   currentIteration === config.config.mapelites.numIterations ? 'Completed' : 
                   currentIteration > 0 ? 'Paused' : 'Ready to start'}
                </span>
              </div>
            </div>
          )}
          
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
                Use filters to browse by run and model, then select rows.
              </p>

              <SolutionsBrowser
                problemId={config?.id}
                metrics={config?.config?.metrics}
                selectionMode
                selectedIds={selectedSolutions}
                onChangeSelectedIds={setSelectedSolutions}
                selectViableOnly
              />
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
