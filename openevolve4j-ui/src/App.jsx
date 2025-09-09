import React, { useState, useEffect, useCallback, useRef, createContext } from 'react';
import ConfigManager from './components/ConfigManager'
import WebSocketService from './services/WebSocketService';
import './App.css'

// Create context for configs
export const ConfigContext = createContext();

function App() {
  const ws = useRef(WebSocketService.getInstance());
  const [configs, setConfigs] = useState([]);
  
  const sendWsMessage = (event) => {
    return ws.current.send({
      payload: event
    });
  };

  const sendWsRequest = (event, timeout) => {
    return ws.current.sendRequest(event, timeout);
  };

  useEffect(() => {
    ws.current.addListener((eventType, data) => {
      console.log(`Received event: ${eventType}`, data);
      
      // Handle connection establishment
      if (eventType === 'open') {
        // Send CONNECT request when WebSocket opens
        sendWsRequest({ type: 'CONNECT' })
          .then(response => {
            if (response && response.existing) {
              const configArray = Object.entries(response.existing).map(([id, config]) => ({
                id,
                name: config.name || id,
                created: config.created || new Date().toISOString(),
                modified: config.modified || new Date().toISOString(),
                config: config
              }));
              setConfigs(configArray);
            }
          })
          .catch(error => {
            console.error('Failed to connect and load configs:', error);
          });
      }
      
      // Handle broadcast events (events without specific request correlation)
      if (eventType === 'message' && data.payload && data.payload.type) {
        switch (data.payload.type) {
          case 'STARTED':
            console.log('Process started:', data.payload.problem);
            break;
          case 'STOPPED':
            console.log('Process stopped:', data.payload.problem);
            break;
          case 'CONFIG_CREATED':
            if (data.payload.config) {
              const newConfig = {
                id: data.payload.id,
                name: data.payload.config.name || data.payload.id,
                created: new Date().toISOString(),
                modified: new Date().toISOString(),
                config: data.payload.config
              };
              setConfigs(prev => [...prev, newConfig]);
            }
            break;
          case 'CONFIG_UPDATED':
            if (data.payload.config && data.payload.id) {
              const updatedConfig = {
                id: data.payload.id,
                name: data.payload.config.name || data.payload.id,
                modified: new Date().toISOString(),
                config: data.payload.config
              };
              setConfigs(prev => prev.map(c => 
                c.id === data.payload.id ? updatedConfig : c
              ));
            }
            break;
          case 'CONFIG_DELETED':
            if (data.payload.id) {
              setConfigs(prev => prev.filter(c => c.id !== data.payload.id));
            }
            break;
        }
      }
    });
  }, []);

  return (
    <ConfigContext.Provider value={{ configs, setConfigs, sendWsMessage, sendWsRequest }}>
      <div className="App">
        <ConfigManager />
      </div>
    </ConfigContext.Provider>
  )
}

export default App
