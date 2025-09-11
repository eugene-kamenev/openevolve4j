import React, { useState, useEffect, useRef, createContext } from 'react';
import { Settings, Plus, RefreshCw, CloudUpload, FolderOpen, Database, Activity, Layers, Target } from 'lucide-react';
import ConfigForm from './components/ConfigForm';
import SidebarConfigList from './components/SidebarConfigList';
import SolutionsView from './components/SolutionsView';
import EvolutionView from './components/EvolutionView';
import WebSocketService from './services/WebSocketService';
import { OpenEvolveConfig } from './Entity';
import './design-system.css';
import './App.css';

// Create context for configs
export const ConfigContext = createContext();

function App() {
  const ws = useRef(WebSocketService.getInstance());
  const [configs, setConfigs] = useState([]);
  const [solutions, setSolutions] = useState({}); // Map of configId -> solutions array
  const [wsStatus, setWsStatus] = useState('connecting'); // connecting | open | error
  const [selectedConfig, setSelectedConfig] = useState(null);
  const [viewMode, setViewMode] = useState('welcome'); // 'welcome', 'edit', 'create'
  const [activeTab, setActiveTab] = useState('configuration'); // 'configuration', 'solutions', 'checkpoints'
  const [lastSync, setLastSync] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const sendWsMessage = (event) => ws.current.send({ payload: event });
  const sendWsRequest = (event, timeout) => ws.current.sendRequest(event, timeout);

  const createDefaultConfig = () => {
    return new OpenEvolveConfig({
      promptPath: "prompts",
      solution: {
        path: "solution",
        runner: "run.sh",
        evalTimeout: "PT120S",
        fullRewrite: true,
        language: "python",
        pattern: ".*\\.py$"
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
      migration: {
        rate: 0.1,
        interval: 10
      },
      repository: {
        checkpointInterval: 10,
        populationSize: 50,
        archiveSize: 10,
        islands: 2
      },
      mapelites: {
        numIterations: 100,
        bins: 10,
        dimensions: ["score", "complexity", "diversity"]
      },
      llm: {
        models: [
          { model: "gpt-4", temperature: 0.7 },
          { model: "claude-3", temperature: 0.8 }
        ],
        apiUrl: "https://api.openai.com/v1",
        apiKey: ""
      },
      metrics: {
        score: true,
        complexity: true,
        diversity: true,
        performance: false
      }
    });
  };

  const handleSelectConfig = (config) => {
    setSelectedConfig(config);
    setViewMode('edit');
    setActiveTab('configuration'); // Reset to configuration tab when selecting config
  };

  const handleCreateNew = () => {
    setSelectedConfig(createDefaultConfig());
    setViewMode('create');
    setActiveTab('configuration'); // Reset to configuration tab when creating
  };

  const handleSave = async (configData) => {
    setLoading(true);
    setError(null);
    
    try {
      if (viewMode === 'create') {
        const configId = Date.now().toString();
        
        const response = await sendWsRequest({
          type: 'CONFIG_CREATE',
          id: configId,
          config: configData
        });
        
        const newConfig = {
          id: configId,
          name: configData.name || `Config ${configs.length + 1}`,
          created: new Date().toISOString(),
          modified: new Date().toISOString(),
          config: configData
        };
        setConfigs(prev => [...prev, newConfig]);
        setSelectedConfig(newConfig);
        setViewMode('edit');
        
      } else if (viewMode === 'edit') {
        const response = await sendWsRequest({
          type: 'CONFIG_UPDATE',
          id: selectedConfig.id,
          config: configData
        });
        
        const updatedConfig = {
          ...selectedConfig,
          config: configData,
          modified: new Date().toISOString()
        };
        setConfigs(prev => prev.map(c =>
          c.id === selectedConfig.id ? updatedConfig : c
        ));
        setSelectedConfig(updatedConfig);
      }
    } catch (error) {
      console.error('Error saving configuration:', error);
      setError(`Failed to save configuration: ${error.message}`);
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

  // Connection + event handling
  useEffect(() => {
    const listener = (eventType, data) => {
      if (eventType === 'open') {
        setWsStatus('open');
        sendWsRequest({ type: 'CONNECT' })
          .then(response => {
            if (response && response.existing) {
              const configArray = Object.entries(response.existing).map(([id, config]) => ({
                id,
                name: config.name || id,
                created: config.created || new Date().toISOString(),
                modified: config.modified || new Date().toISOString(),
                config
              }));
              setConfigs(configArray);
              setLastSync(new Date());
            }
          })
          .catch(err => {
            console.error('Failed to connect and load configs:', err);
            setWsStatus('error');
          });
      } else if (eventType === 'close') {
        setWsStatus('error');
      }

      if (eventType === 'message' && data.payload?.type) {
        const p = data.payload;
        switch (p.type) {
          case 'CONFIG_CREATED':
            if (p.config) {
              setConfigs(prev => [...prev, { id: p.id, name: p.config.name || p.id, created: new Date().toISOString(), modified: new Date().toISOString(), config: p.config }]);
            }
            break;
          case 'CONFIG_UPDATED':
            if (p.config && p.id) {
              setConfigs(prev => prev.map(c => c.id === p.id ? { ...c, name: p.config.name || p.id, modified: new Date().toISOString(), config: p.config } : c));
            }
            break;
          case 'CONFIG_DELETED':
            if (p.id) setConfigs(prev => prev.filter(c => c.id !== p.id));
            break;
        }
      }
    };
    ws.current.addListener(listener);
  }, []);

  const statusDot = (state) => {
    const color = state === 'open' ? 'var(--oe-success)' : state === 'error' ? 'var(--oe-danger)' : 'var(--oe-warn)';
    return <span style={{ width:10, height:10, borderRadius:50, background:color, display:'inline-block', boxShadow:`0 0 0 3px rgba(0,0,0,.4)` }} />
  };

  return (
    <ConfigContext.Provider value={{ configs, setConfigs, solutions, setSolutions, sendWsMessage, sendWsRequest }}>
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
              <span>{wsStatus==='open' ? 'Connected' : wsStatus==='error' ? 'Disconnected' : 'Connecting…'}</span>
            </div>
            {lastSync && <div className="text-faint" style={{marginTop:6}}>Synced {lastSync.toLocaleTimeString()}</div>}
          </div>
        </aside>

        {/* Top Bar */}
        <header className="oe-topbar">
          <div className="topbar-left">
            <h2 className="mb-0" style={{fontSize:18, fontWeight:600}}>
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
            <button className="oe-btn outline" onClick={() => setLastSync(new Date())}>
              <RefreshCw size={16}/>Refresh
            </button>
          </div>
        </header>

        {/* Main Content */}
        <main className="oe-content">
          {error && (
            <div className="oe-surface p-4" style={{borderColor:'var(--oe-danger)', marginBottom: 16}}>
              <div className="row justify-between align-center">
                <span style={{color:'var(--oe-danger)'}}>{error}</span>
                <button className="oe-btn ghost sm" onClick={()=>setError(null)}>Dismiss</button>
              </div>
            </div>
          )}
          
          {loading && (
            <div className="oe-surface p-4" style={{borderColor:'var(--oe-accent)', marginBottom: 16}}>
              <span className="text-dim">Working…</span>
            </div>
          )}

          <div className="oe-surface elevated p-5 animate-fade-in">
            {viewMode === 'welcome' && (
              <div className="welcome-content">
                <h3 style={{marginTop:0}}>Welcome to OpenEvolve</h3>
                <p className="text-faint">Select a configuration from the sidebar to edit it, or create a new one to get started.</p>
                <button className="oe-btn primary" onClick={handleCreateNew}>
                  <Plus size={16}/> Create New Configuration
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
