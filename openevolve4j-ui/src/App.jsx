import React, { useState, useEffect, useRef, createContext } from 'react';
import { Settings, Plus, RefreshCw, CloudUpload, FolderOpen, Database, Activity, Layers } from 'lucide-react';
import ConfigManager from './components/ConfigManager';
import WebSocketService from './services/WebSocketService';
import './design-system.css';
import './App.css';

// Create context for configs
export const ConfigContext = createContext();

function App() {
  const ws = useRef(WebSocketService.getInstance());
  const [configs, setConfigs] = useState([]);
  const [wsStatus, setWsStatus] = useState('connecting'); // connecting | open | error
  const [activeNav, setActiveNav] = useState('configs');
  const [lastSync, setLastSync] = useState(null);

  const sendWsMessage = (event) => ws.current.send({ payload: event });
  const sendWsRequest = (event, timeout) => ws.current.sendRequest(event, timeout);

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
    <ConfigContext.Provider value={{ configs, setConfigs, sendWsMessage, sendWsRequest }}>
      <div className="oe-app-layout">
        {/* Sidebar */}
        <aside className="oe-sidebar">
          <div className="oe-logo">
            <Settings />
            <span>OpenEvolve</span>
          </div>
          <nav className="oe-nav">
            <div className="oe-nav-group">
              <button className={`oe-nav-btn ${activeNav==='configs'?'active':''}`} onClick={() => setActiveNav('configs')}>
                <Database /> <span className="label">Configs</span>
              </button>
              <button className={`oe-nav-btn ${activeNav==='activity'?'active':''}`} onClick={() => setActiveNav('activity')}>
                <Activity /> <span className="label">Activity</span>
              </button>
              <button className={`oe-nav-btn ${activeNav==='agents'?'active':''}`} onClick={() => setActiveNav('agents')}>
                <Layers /> <span className="label">Agents</span>
              </button>
            </div>
          </nav>
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
          <h2 className="mb-0" style={{fontSize:18, fontWeight:600}}>Configuration Manager</h2>
          <div className="row gap-3">
            <button className="oe-btn outline" onClick={() => setLastSync(new Date())}><RefreshCw size={16}/>Refresh</button>
            <label className="oe-btn outline" style={{cursor:'pointer'}}>
              <CloudUpload size={16}/> Import
              <input type="file" accept=".yml,.yaml,.json" style={{display:'none'}} onChange={(e)=>{
                // fire a synthetic event to ConfigManager via custom event bus in future; for now rely on inside component UI
              }} />
            </label>
          </div>
        </header>

        {/* Main Content */}
        <main className="oe-content">
          {activeNav === 'configs' && <ConfigManager />}
          {activeNav !== 'configs' && (
            <div className="oe-surface p-5" style={{minHeight:320}}>
              <h3 style={{marginTop:0}}>Coming Soon</h3>
              <p className="text-faint">The <strong>{activeNav}</strong> section is under construction.</p>
            </div>
          )}
        </main>
      </div>
    </ConfigContext.Provider>
  );
}

export default App
