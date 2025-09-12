class WebSocketService {
  static instance = null;
  socket = null;
  listeners = [];
  isConnected = false;
  pendingRequests = new Map(); // Store pending requests by ID
  eventIdCounter = 0;
  
  constructor() {
    if (WebSocketService.instance) {
      return WebSocketService.instance;
    }
    WebSocketService.instance = this;
    this.connect();
  }
  
  connect() {
    if (this.socket && (this.socket.readyState === WebSocket.CONNECTING || this.socket.readyState === WebSocket.OPEN)) {
      return; // Already connected or connecting
    }
    
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    this.socket = new WebSocket(`${protocol}//${host.split(":")[0]}:7070/ws`);
    
    this.socket.onopen = () => {
      console.log('WebSocket connection established');
      this.isConnected = true;
      this.notifyListeners('open', null);
    };

    this.socket.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        
        // Check if this is a response to a pending request
        if (data.id && this.pendingRequests.has(data.id)) {
          const pendingRequest = this.pendingRequests.get(data.id);
          this.pendingRequests.delete(data.id);
          
          // Resolve the promise with the payload
          pendingRequest.resolve(data.payload);
          return;
        }
        
        // Notify general listeners for non-request-response messages
        this.notifyListeners('message', data);
      } catch (error) {
        console.error('Error parsing WebSocket message:', error);
      }
    };

    this.socket.onerror = (error) => {
      console.error('WebSocket error:', error);
      this.notifyListeners('error', error);
    };

    this.socket.onclose = () => {
      console.log('WebSocket connection closed');
      this.isConnected = false;
      this.notifyListeners('close', null);
      
      // Clear any pending requests when connection is lost
      this.pendingRequests.forEach((pendingRequest) => {
        pendingRequest.reject(new Error('WebSocket connection lost'));
      });
      this.pendingRequests.clear();
    };
  }
  
  send(message) {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      console.error('WebSocket is not connected');
      return false;
    }
    
    if (typeof message === 'object') {
      this.socket.send(JSON.stringify(message));
    } else {
      this.socket.send(message);
    }
    return true;
  }

  // Send a request and wait for response with matching ID
  sendRequest(payload, timeout = 30000) {
    return new Promise((resolve, reject) => {
      if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
        reject(new Error('WebSocket is not connected'));
        return;
      }

      const id = `req_${++this.eventIdCounter}_${Date.now()}`;
      const message = { id, payload };

      // Store the promise resolvers
      this.pendingRequests.set(id, { resolve, reject });

      // Set timeout
      const timeoutId = setTimeout(() => {
        if (this.pendingRequests.has(id)) {
          this.pendingRequests.delete(id);
          reject(new Error(`Request timeout after ${timeout}ms`));
        }
      }, timeout);

      // Clean up timeout when request completes
      const originalResolve = resolve;
      const originalReject = reject;
      this.pendingRequests.set(id, {
        resolve: (result) => {
          clearTimeout(timeoutId);
          originalResolve(result);
        },
        reject: (error) => {
          clearTimeout(timeoutId);
          originalReject(error);
        }
      });

      // Send the message
      this.socket.send(JSON.stringify(message));
    });
  }
  
  addListener(listener) {
    // Prevent duplicate listeners
    if (!this.listeners.includes(listener)) {
      this.listeners.push(listener);
      console.log(`WebSocket listener added. Total listeners: ${this.listeners.length}`);
      // If already connected, notify the new listener
      if (this.isConnected) {
        listener('open', null);
      }
    } else {
      console.warn('Attempted to add duplicate WebSocket listener');
    }
  }
  
  removeListener(listener) {
    const index = this.listeners.indexOf(listener);
    if (index !== -1) {
      this.listeners.splice(index, 1);
      console.log(`WebSocket listener removed. Total listeners: ${this.listeners.length}`);
    } else {
      console.warn('Attempted to remove non-existent WebSocket listener');
    }
  }
  
  notifyListeners(event, data) {
    this.listeners.forEach(listener => {
      listener(event, data);
    });
  }
  
  close() {
    if (this.socket) {
      this.socket.close();
    }
  }
  
  // Singleton instance getter
  static getInstance() {
    if (!WebSocketService.instance) {
      new WebSocketService();
    }
    return WebSocketService.instance;
  }

  // Check connection status
  isSocketConnected() {
    return this.isConnected;
  }
}

export default WebSocketService;
