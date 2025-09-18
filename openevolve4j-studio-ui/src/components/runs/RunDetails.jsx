import React, { useState } from 'react';
import { useApiData } from '../../hooks/index.jsx';
import { 
  evolutionRunService, 
  evolutionProblemService, 
  evolutionSolutionService, 
  evolutionStateService 
} from '../../services/index.jsx';
import { Modal, Button, Card, LoadingSpinner, ErrorMessage, EmptyState } from '../common/index.jsx';

export default function RunDetails({ isOpen, onClose, run, onUpdate }) {
  const [activeTab, setActiveTab] = useState('overview');

  // Fetch related data
  const { data: problem, loading: problemLoading } = useApiData(
    () => evolutionProblemService.getById(run.problemId),
    [run.problemId]
  );

  const { data: solutionsData, loading: solutionsLoading, refetch: refetchSolutions } = useApiData(
    () => evolutionSolutionService.getSolutionsByRun(run.id),
    [run.id]
  );

  const { data: statesData, loading: statesLoading, refetch: refetchStates } = useApiData(
    () => evolutionStateService.getStateHistory(run.id, 20),
    [run.id]
  );

  const solutions = solutionsData?.list || [];
  const states = statesData?.list || [];

  const handleStopRun = async () => {
    try {
      await evolutionRunService.stopRun(run.id);
      onUpdate();
      alert('Run stopped successfully');
    } catch (error) {
      alert('Failed to stop run: ' + error.message);
    }
  };

  const tabs = [
    { id: 'overview', label: 'Overview' },
    { id: 'solutions', label: `Solutions (${solutions.length})` },
    { id: 'states', label: `States (${states.length})` },
    { id: 'configuration', label: 'Configuration' },
  ];

  return (
    <Modal 
      isOpen={isOpen} 
      onClose={onClose} 
      title={`Run ${run.id?.substring(0, 8)}...`}
      maxWidth="max-w-6xl"
    >
      <div className="space-y-6">
        {/* Header Actions */}
        <div className="flex justify-between items-center">
          <div className="text-sm text-gray-500">
            <div>Run ID: {run.id}</div>
            <div>Started: {new Date(run.dateCreated).toLocaleString()}</div>
          </div>
          <div className="flex space-x-2">
            <Button onClick={handleStopRun} variant="danger" size="small">
              Stop Run
            </Button>
          </div>
        </div>

        {/* Tabs */}
        <div className="border-b border-gray-200">
          <nav className="-mb-px flex space-x-8">
            {tabs.map((tab) => (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`py-2 px-1 border-b-2 font-medium text-sm ${
                  activeTab === tab.id
                    ? 'border-blue-500 text-blue-600'
                    : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
                }`}
              >
                {tab.label}
              </button>
            ))}
          </nav>
        </div>

        {/* Tab Content */}
        <div className="min-h-96">
          {activeTab === 'overview' && (
            <OverviewTab 
              run={run} 
              problem={problem} 
              problemLoading={problemLoading}
              solutions={solutions}
              states={states}
            />
          )}
          {activeTab === 'solutions' && (
            <SolutionsTab 
              solutions={solutions} 
              loading={solutionsLoading} 
              onRefresh={refetchSolutions}
            />
          )}
          {activeTab === 'states' && (
            <StatesTab 
              states={states} 
              loading={statesLoading} 
              onRefresh={refetchStates}
            />
          )}
          {activeTab === 'configuration' && (
            <ConfigurationTab config={run.config} />
          )}
        </div>
      </div>
    </Modal>
  );
}

function OverviewTab({ run, problem, problemLoading, solutions, states }) {
  const formatDuration = (dateCreated) => {
    const start = new Date(dateCreated);
    const now = new Date();
    const diffMs = now - start;
    const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
    const diffMinutes = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));
    
    if (diffHours > 0) {
      return `${diffHours}h ${diffMinutes}m`;
    }
    return `${diffMinutes}m`;
  };

  const getTopSolutions = () => {
    if (!solutions.length) return [];
    
    return solutions
      .filter(s => s.fitness && s.fitness.score !== undefined)
      .sort((a, b) => (b.fitness.score || 0) - (a.fitness.score || 0))
      .slice(0, 5);
  };

  const topSolutions = getTopSolutions();
  const latestState = states.length > 0 ? states[0] : null;

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
      {/* Run Information */}
      <Card title="Run Information">
        <div className="space-y-3">
          <InfoRow label="Run ID" value={run.id} />
          <InfoRow label="Started" value={new Date(run.dateCreated).toLocaleString()} />
          <InfoRow label="Duration" value={formatDuration(run.dateCreated)} />
          <InfoRow 
            label="Problem" 
            value={problemLoading ? 'Loading...' : (problem?.name || 'Unknown')} 
          />
          <InfoRow label="Total Solutions" value={solutions.length} />
          <InfoRow label="State Updates" value={states.length} />
        </div>
      </Card>

      {/* Current Status */}
      <Card title="Current Status">
        {latestState ? (
          <div className="space-y-3">
            <InfoRow 
              label="Last Update" 
              value={new Date(latestState.dateCreated).toLocaleString()} 
            />
            {latestState.state && (
              <>
                <InfoRow 
                  label="Generation" 
                  value={latestState.state.generation || 'N/A'} 
                />
                <InfoRow 
                  label="Best Fitness" 
                  value={latestState.state.bestFitness || 'N/A'} 
                />
                <InfoRow 
                  label="Population Size" 
                  value={latestState.state.populationSize || 'N/A'} 
                />
              </>
            )}
          </div>
        ) : (
          <div className="text-gray-500 text-sm">No state information available</div>
        )}
      </Card>

      {/* Configuration Summary */}
      <Card title="Configuration Summary">
        <div className="space-y-3">
          <InfoRow 
            label="Iterations" 
            value={run.config?.mapelites?.numIterations || 'N/A'} 
          />
          <InfoRow 
            label="Population Size" 
            value={run.config?.repository?.populationSize || 'N/A'} 
          />
          <InfoRow 
            label="Bins" 
            value={run.config?.mapelites?.bins || 'N/A'} 
          />
          <InfoRow 
            label="Language" 
            value={run.config?.solution?.language || 'N/A'} 
          />
        </div>
      </Card>

      {/* Top Solutions */}
      <Card title="Top Solutions">
        {topSolutions.length > 0 ? (
          <div className="space-y-2">
            {topSolutions.map((solution, index) => (
              <div key={solution.id} className="flex justify-between items-center py-2 border-b border-gray-100 last:border-b-0">
                <div>
                  <div className="text-sm font-medium">
                    #{index + 1} - {solution.id?.substring(0, 8)}...
                  </div>
                  <div className="text-xs text-gray-500">
                    {new Date(solution.dateCreated).toLocaleString()}
                  </div>
                </div>
                <div className="text-sm font-medium">
                  Score: {solution.fitness?.score?.toFixed(3) || 'N/A'}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="text-gray-500 text-sm">No solutions with fitness scores yet</div>
        )}
      </Card>
    </div>
  );
}

function SolutionsTab({ solutions, loading, onRefresh }) {
  if (loading) {
    return <LoadingSpinner size="large" className="py-12" />;
  }

  if (!solutions || solutions.length === 0) {
    return (
      <EmptyState
        title="No solutions found"
        description="This run hasn't generated any solutions yet."
        action={
          <Button onClick={onRefresh} variant="secondary">
            Refresh
          </Button>
        }
      />
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <h4 className="text-lg font-medium">Solutions ({solutions.length})</h4>
        <Button onClick={onRefresh} variant="secondary" size="small">
          Refresh
        </Button>
      </div>

      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Solution ID
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Created
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Fitness
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                Parent
              </th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {solutions.map((solution) => (
              <tr key={solution.id} className="hover:bg-gray-50">
                <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                  {solution.id?.substring(0, 12)}...
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                  {new Date(solution.dateCreated).toLocaleString()}
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                  {solution.fitness ? (
                    <div>
                      {Object.entries(solution.fitness).map(([key, value]) => (
                        <div key={key}>
                          {key}: {typeof value === 'number' ? value.toFixed(3) : value}
                        </div>
                      ))}
                    </div>
                  ) : (
                    'N/A'
                  )}
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                  {solution.parentId ? `${solution.parentId.substring(0, 8)}...` : 'Root'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function StatesTab({ states, loading, onRefresh }) {
  if (loading) {
    return <LoadingSpinner size="large" className="py-12" />;
  }

  if (!states || states.length === 0) {
    return (
      <EmptyState
        title="No state history found"
        description="No state updates have been recorded for this run yet."
        action={
          <Button onClick={onRefresh} variant="secondary">
            Refresh
          </Button>
        }
      />
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <h4 className="text-lg font-medium">State History ({states.length})</h4>
        <Button onClick={onRefresh} variant="secondary" size="small">
          Refresh
        </Button>
      </div>

      <div className="space-y-3">
        {states.map((state, index) => (
          <div key={index} className="border border-gray-200 rounded-md p-4">
            <div className="flex justify-between items-start mb-2">
              <div className="text-sm font-medium text-gray-900">
                State Update #{states.length - index}
              </div>
              <div className="text-xs text-gray-500">
                {new Date(state.dateCreated).toLocaleString()}
              </div>
            </div>
            
            {state.state && (
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                {Object.entries(state.state).map(([key, value]) => (
                  <div key={key}>
                    <span className="text-gray-600">{key}:</span>
                    <div className="font-medium">
                      {typeof value === 'object' ? JSON.stringify(value) : value}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

function ConfigurationTab({ config }) {
  return (
    <div className="space-y-6">
      <Card title="Run Configuration">
        <pre className="bg-gray-50 p-4 rounded-md text-sm overflow-auto max-h-96">
          {JSON.stringify(config, null, 2)}
        </pre>
      </Card>
    </div>
  );
}

function InfoRow({ label, value }) {
  return (
    <div className="flex justify-between">
      <span className="text-gray-600 text-sm">{label}:</span>
      <span className="font-medium text-sm text-right">{value}</span>
    </div>
  );
}
