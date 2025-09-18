
import React, { useState } from 'react';
import { 
  Dashboard, 
  ProblemsList, 
  RunsList, 
  SolutionsList,
  Button 
} from './components/index.jsx';

function App() {
  const [currentView, setCurrentView] = useState('dashboard');

  const navigation = [
    { id: 'dashboard', label: 'Dashboard', icon: '📊' },
    { id: 'problems', label: 'Problems', icon: '🧬' },
    { id: 'runs', label: 'Runs', icon: '🚀' },
    { id: 'solutions', label: 'Solutions', icon: '💡' },
  ];

  const renderContent = () => {
    switch (currentView) {
      case 'dashboard':
        return <Dashboard />;
      case 'problems':
        return <ProblemsList />;
      case 'runs':
        return <RunsList />;
      case 'solutions':
        return <SolutionsList />;
      default:
        return <Dashboard />;
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Navigation Header */}
      <nav className="bg-white shadow-sm border-b border-gray-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex">
              {/* Logo */}
              <div className="flex-shrink-0 flex items-center">
                <h1 className="text-xl font-bold text-gray-900">
                  OpenEvolve4j Studio
                </h1>
              </div>

              {/* Navigation Links */}
              <div className="hidden sm:ml-6 sm:flex sm:space-x-8">
                {navigation.map((item) => (
                  <button
                    key={item.id}
                    onClick={() => setCurrentView(item.id)}
                    className={`inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium transition-colors ${
                      currentView === item.id
                        ? 'border-blue-500 text-gray-900'
                        : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700'
                    }`}
                  >
                    <span className="mr-2">{item.icon}</span>
                    {item.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Right side actions */}
            <div className="flex items-center space-x-4">
              <div className="text-sm text-gray-500">
                {new Date().toLocaleDateString()}
              </div>
            </div>
          </div>
        </div>

        {/* Mobile menu */}
        <div className="sm:hidden">
          <div className="pt-2 pb-3 space-y-1">
            {navigation.map((item) => (
              <button
                key={item.id}
                onClick={() => setCurrentView(item.id)}
                className={`block pl-3 pr-4 py-2 text-base font-medium w-full text-left transition-colors ${
                  currentView === item.id
                    ? 'bg-blue-50 border-blue-500 text-blue-700 border-l-4'
                    : 'border-transparent text-gray-500 hover:bg-gray-50 hover:text-gray-700'
                }`}
              >
                <span className="mr-2">{item.icon}</span>
                {item.label}
              </button>
            ))}
          </div>
        </div>
      </nav>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
        <div className="px-4 sm:px-0">
          {renderContent()}
        </div>
      </main>
    </div>
  );
}

export default App;
