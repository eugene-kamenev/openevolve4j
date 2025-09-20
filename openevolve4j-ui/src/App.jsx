import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Settings, Plus, RefreshCw, Activity, Target } from 'lucide-react';
import ConfigForm from './components/ConfigForm';
import SidebarConfigList from './components/SidebarConfigList';
import SolutionsView from './components/SolutionsView';
import EvolutionView from './components/EvolutionView';
import WebSocketService from './services/WebSocketService';
import { OpenEvolveConfig } from './Entity';
import { ProblemsApi, RunsApi, SolutionsApi } from './services/api';
import { ConfigContext } from './ConfigContext';
import './design-system.css';
import './App.css';

function App() {
  const ws = useRef(WebSocketService.getInstance());
  const [configs, setConfigs] = useState([]); // Evolution problems
  const [solutions, setSolutions] = useState({}); // Map problemId -> solutions[]
  const [bestSolutions, setBestSolutions] = useState({}); // Map problemId -> best solution
  const [statuses, setStatuses] = useState({}); // Map problemId -> 'RUNNING'|'NOT_RUNNING'
  const [activeRuns, setActiveRuns] = useState({}); // Map problemId -> current runId
  const [wsStatus, setWsStatus] = useState('connecting'); // connecting | open | error
  const [selectedConfig, setSelectedConfig] = useState(null);
  const [viewMode, setViewMode] = useState('welcome'); // 'welcome', 'edit', 'create'
  const [activeTab, setActiveTab] = useState('configuration'); // 'configuration', 'solutions', 'evolution'
  const [lastSync, setLastSync] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [evolutionEvents, setEvolutionEvents] = useState({}); // Map problemId or runId -> events

  // Helper: compute best solution by metric 'score' if present
  const computeBest = useCallback((list, metrics) => {
    if (!Array.isArray(list) || list.length === 0) return null;
    // Prefer numeric 'score' higher is better by default
    const key = metrics && typeof metrics === 'object' ? Object.keys(metrics)[0] : 'score';
    return list.reduce((best, cur) => {
      const b = best?.fitness?.[key];
      const c = cur?.fitness?.[key];
      if (b == null) return cur;
      if (c == null) return best;
      return c > b ? cur : best;
    }, null);
  }, []);

  // REST: load all problems
  const loadProblems = useCallback(async () => {
    const resp = await ProblemsApi.list({ limit: 50, offset: 0, sort: 'name', order: 'asc' });
    const list = resp?.list ?? [];
    setConfigs(list);
    setLastSync(new Date());
  }, []);

  // Load latest run and solutions for a problem
  const fetchSolutions = useCallback(async (problemId) => {
    if (!problemId) return;
    // Find latest run for the problem
    const runsResp = await RunsApi.list({ filters: { forProblem: problemId }, sort: 'dateCreated', order: 'desc', limit: 1, offset: 0 });
    const latestRun = (runsResp?.list ?? [])[0];
    if (!latestRun) {
      setSolutions(prev => ({ ...prev, [problemId]: [] }));
      setActiveRuns(prev => ({ ...prev, [problemId]: undefined }));
      return;
    }
    const runId = latestRun.id;
    setActiveRuns(prev => ({ ...prev, [problemId]: runId }));
    const solsResp = await SolutionsApi.listForRun(runId, { limit: 200, offset: 0, sort: 'dateCreated', order: 'desc' });
    const list = solsResp?.list ?? [];
    setSolutions(prev => ({ ...prev, [problemId]: list }));
    // Compute best
    const problem = configs.find(c => c.id === problemId);
    const metrics = problem?.config?.metrics;
    const best = computeBest(list, metrics);
    if (best) setBestSolutions(prev => ({ ...prev, [problemId]: best }));
  }, [configs, computeBest]);

  // Create default config
  const createDefaultConfig = () => {
    return new OpenEvolveConfig({
      type: 'MAPELITES',
      promptPath: 'prompts',
      solution: {
        path: 'solution',
        runner: 'run.sh',
        evalTimeout: 'PT120S',
        fullRewrite: true,
        language: 'python',
        pattern: '.*\\.py$'
      },
      selection: {
        seed: 42,
        explorationRatio: 0.1,
        exploitationRatio: 0.1,
        eliteSelectionRatio: 0.1,
        numInspirations: 5,
        numberDiverse: 5,
        numberTop: 5
      },
      migration: { rate: 0.1, interval: 10 },
      repository: { checkpointInterval: 10, populationSize: 50, archiveSize: 10, islands: 2 },
      mapelites: { numIterations: 100, bins: 10, dimensions: ['score', 'complexity', 'diversity'] },
      llm: { models: [], apiUrl: '', apiKey: '' },
      metrics: { score: true, complexity: true, diversity: true }
    });
  };

  const handleSelectConfig = (config) => {
    setSelectedConfig(config);
    setViewMode('edit');
    setActiveTab('configuration');
  };

  // Fetch solutions when a config is selected for editing
  useEffect(() => {
    if (selectedConfig?.id && viewMode === 'edit') {
      fetchSolutions(selectedConfig.id).catch(() => {});
    }
  }, [selectedConfig?.id, viewMode, fetchSolutions]);

  const handleCreateNew = () => {
    setSelectedConfig(createDefaultConfig());
    setViewMode('create');
    setActiveTab('configuration');
  };

  const handleSave = async (configData) => {
    setLoading(true);
    setError(null);
    try {
      if (viewMode === 'create') {
        // Create new EvolutionProblem
        const created = await ProblemsApi.create({ name: configData.name || `Config ${configs.length + 1}`, config: configData });
        await loadProblems();
        setSelectedConfig(created);
        setViewMode('edit');
      } else if (viewMode === 'edit') {
        // Update problem: send partial update of config/name
        const id = selectedConfig.id;
        const updated = await ProblemsApi.update(id, { name: configData.name || selectedConfig.name, config: configData });
        // Replace local
        setConfigs(prev => prev.map(c => (c.id === id ? updated : c)));
        setSelectedConfig(updated);
      }
    } catch (e) {
      console.error('Error saving configuration:', e);
      setError(`Failed to save configuration: ${e.message}`);
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = () => {
    if (selectedConfig && selectedConfig.id) {
      setViewMode('edit');
    } else {
      setViewMode('welcome');
      setSelectedConfig(null);
    }
  };

  // Initial load of problems
  useEffect(() => {
    loadProblems().catch((e) => setError(e.message));
  }, [loadProblems]);

  // WebSocket: treat as events-only; no request/response
  useEffect(() => {
    const listener = (eventType, data) => {
      if (eventType === 'open') {
        setWsStatus('open');
      } else if (eventType === 'close') {
        setWsStatus('error');
      }

      if (eventType === 'message') {
        // Support both { payload: {...} } and bare {...}
        const p = data?.payload ?? data;
        if (!p || !p.type) return;
        switch (p.type) {
          case 'EVOLUTION_EVENT': {
            const ts = new Date().toISOString();
            const taskKey = p.taskId || p.problemId || p.runId; // backend may differ
            if (taskKey) {
              setEvolutionEvents(prev => ({ ...prev, [taskKey]: [...(prev[taskKey] || []), { ...p.event, timestamp: ts }] }));
            }
            break;
          }
          default:
            break;
        }
      }
    };
    ws.current.addListener(listener);
    return () => ws.current.removeListener(listener);
  }, []);

  const statusDot = (state) => {
    const color = state === 'open' ? 'var(--oe-success)' : state === 'error' ? 'var(--oe-danger)' : 'var(--oe-warn)';
    return <span style={{ width: 10, height: 10, borderRadius: 50, background: color, display: 'inline-block', boxShadow: `0 0 0 3px rgba(0,0,0,.4)` }} />
  };

  return (
    <ConfigContext.Provider value={{
      configs,
      setConfigs,
      solutions,
      setSolutions,
      bestSolutions,
      setBestSolutions,
      statuses,
      setStatuses,
      activeRuns,
      setActiveRuns,
      evolutionEvents,
      setEvolutionEvents,
      fetchSolutions,
      loadProblems
    }}>
      <div className="oe-app-layout">
        {/* Sidebar */}
        <aside className="oe-sidebar">
          <div className="oe-logo">
            <Settings />
            <span>OpenEvolve</span>
          </div>

          <SidebarConfigList
            selectedConfigId={selectedConfig?.id}
            onSelectConfig={handleSelectConfig}
            onCreateNew={handleCreateNew}
          />

          <div className="oe-footer">
            <div className="row gap-2 align-center">
              {statusDot(wsStatus)}
              <span>{wsStatus === 'open' ? 'Events connected' : wsStatus === 'error' ? 'Events disconnected' : 'Connecting…'}</span>
            </div>
            {lastSync && <div className="text-faint" style={{ marginTop: 6 }}>Synced {lastSync.toLocaleTimeString()}</div>}
          </div>
        </aside>

        {/* Top Bar */}
        <header className="oe-topbar">
          <div className="topbar-left">
            <h2 className="mb-0" style={{ fontSize: 18, fontWeight: 600 }}>
              {viewMode === 'create' ? 'Create New Configuration' :
                viewMode === 'edit' ? `${selectedConfig?.name || 'Configuration'}` :
                  'OpenEvolve Configuration Manager'}
            </h2>

            {/* Tab Navigation for edit mode */}
            {viewMode === 'edit' && (
              <div className="tab-navigation">
                <button
                  className={`tab-btn ${activeTab === 'configuration' ? 'active' : ''}`}
                  onClick={() => setActiveTab('configuration')}
                >
                  <Settings size={14} /> Configuration
                </button>
                <button
                  className={`tab-btn ${activeTab === 'solutions' ? 'active' : ''}`}
                  onClick={() => setActiveTab('solutions')}
                >
                  <Activity size={14} /> Solutions
                </button>
                <button
                  className={`tab-btn ${activeTab === 'evolution' ? 'active' : ''}`}
                  onClick={() => setActiveTab('evolution')}
                >
                  <Target size={14} /> Evolution
                </button>
              </div>
            )}
          </div>

          <div className="row gap-3">
            <button className="oe-btn outline" onClick={() => loadProblems().catch(() => {})}>
              <RefreshCw size={16} />Refresh
            </button>
          </div>
        </header>

        {/* Main Content */}
        <main className="oe-content">
          {error && (
            <div className="oe-surface p-4" style={{ borderColor: 'var(--oe-danger)', marginBottom: 16 }}>
              <div className="row justify-between align-center">
                <span style={{ color: 'var(--oe-danger)' }}>{error}</span>
                <button className="oe-btn ghost sm" onClick={() => setError(null)}>Dismiss</button>
              </div>
            </div>
          )}

          {loading && (
            <div className="oe-surface p-4" style={{ borderColor: 'var(--oe-accent)', marginBottom: 16 }}>
              <span className="text-dim">Working…</span>
            </div>
          )}

          <div className="oe-surface elevated p-5 animate-fade-in">
            {viewMode === 'welcome' && (
              <div className="welcome-content">
                <h3 style={{ marginTop: 0 }}>Welcome to OpenEvolve</h3>
                <p className="text-faint">Select a configuration from the sidebar to edit it, or create a new one to get started.</p>
                <button className="oe-btn primary" onClick={handleCreateNew}>
                  <Plus size={16} /> Create New Configuration
                </button>
              </div>
            )}

            {(viewMode === 'edit' || viewMode === 'create') && (
              <>
                {activeTab === 'configuration' && (
                  <ConfigForm
                    config={selectedConfig}
                    mode={viewMode}
                    onSave={handleSave}
                    onCancel={handleCancel}
                    disabled={loading}
                  />
                )}

                {activeTab === 'solutions' && viewMode === 'edit' && (
                  <SolutionsView config={selectedConfig} />
                )}

                {activeTab === 'evolution' && viewMode === 'edit' && (
                  <EvolutionView config={selectedConfig} />
                )}

              </>
            )}
          </div>
        </main>
      </div>
    </ConfigContext.Provider>
  );
}

export default App
