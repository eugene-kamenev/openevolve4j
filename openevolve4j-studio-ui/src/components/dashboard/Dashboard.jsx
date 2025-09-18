import React from 'react';
import { useApiData } from '../../hooks/index.jsx';
import { apiServices } from '../../services/index.jsx';
import { Card, LoadingSpinner, ErrorMessage, Button } from '../common/index.jsx';

export default function Dashboard() {
  // Fetch dashboard data
  const { data: problemsData, loading: problemsLoading, error: problemsError } = useApiData(
    () => apiServices.problems.getAll({ limit: 5, sort: 'name', order: 'asc' })
  );

  const { data: runsData, loading: runsLoading, error: runsError } = useApiData(
    () => apiServices.runs.getRecentRuns(5)
  );

  const { data: solutionsData, loading: solutionsLoading, error: solutionsError } = useApiData(
    () => apiServices.solutions.getTopSolutions(5)
  );

  const { data: statesData, loading: statesLoading, error: statesError } = useApiData(
    () => apiServices.states.getRecentStates(10)
  );

  const problems = problemsData?.list || [];
  const runs = runsData?.list || [];
  const solutions = solutionsData?.list || [];
  const states = statesData?.list || [];

  // Calculate some basic statistics
  const totalProblems = problemsData?.count || 0;
  const totalRuns = runsData?.count || 0;
  const totalSolutions = solutionsData?.count || 0;
  const totalStates = statesData?.count || 0;

  // Get active runs (runs with recent state updates)
  const activeRuns = states.length > 0 ? 
    [...new Set(states.slice(0, 5).map(s => s.runId))].length : 0;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900">OpenEvolve4j Studio Dashboard</h1>
        <p className="text-gray-600">Overview of your evolution experiments</p>
      </div>

      {/* Statistics Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <StatCard
          title="Problems"
          value={totalProblems}
          loading={problemsLoading}
          error={problemsError}
          icon="🧬"
        />
        <StatCard
          title="Runs"
          value={totalRuns}
          loading={runsLoading}
          error={runsError}
          icon="🚀"
        />
        <StatCard
          title="Solutions"
          value={totalSolutions}
          loading={solutionsLoading}
          error={solutionsError}
          icon="💡"
        />
        <StatCard
          title="Active Runs"
          value={activeRuns}
          loading={statesLoading}
          error={statesError}
          icon="⚡"
        />
      </div>

      {/* Main Content Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Recent Problems */}
        <Card 
          title="Recent Problems" 
          actions={
            <Button size="small" variant="secondary">
              View All
            </Button>
          }
        >
          {problemsLoading ? (
            <LoadingSpinner size="medium" />
          ) : problemsError ? (
            <ErrorMessage error={problemsError} />
          ) : problems.length > 0 ? (
            <div className="space-y-3">
              {problems.map((problem) => (
                <div key={problem.id} className="flex justify-between items-center py-2 border-b border-gray-100 last:border-b-0">
                  <div>
                    <div className="font-medium text-sm">{problem.name}</div>
                    <div className="text-xs text-gray-500">
                      {problem.config?.mapelites?.numIterations || 100} iterations
                    </div>
                  </div>
                  <div className="text-xs text-gray-500">
                    {problem.config?.solution?.language || 'python'}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-gray-500 text-sm">No problems created yet</div>
          )}
        </Card>

        {/* Recent Runs */}
        <Card 
          title="Recent Runs" 
          actions={
            <Button size="small" variant="secondary">
              View All
            </Button>
          }
        >
          {runsLoading ? (
            <LoadingSpinner size="medium" />
          ) : runsError ? (
            <ErrorMessage error={runsError} />
          ) : runs.length > 0 ? (
            <div className="space-y-3">
              {runs.map((run) => (
                <div key={run.id} className="flex justify-between items-center py-2 border-b border-gray-100 last:border-b-0">
                  <div>
                    <div className="font-medium text-sm">
                      Run {run.id?.substring(0, 8)}...
                    </div>
                    <div className="text-xs text-gray-500">
                      {new Date(run.dateCreated).toLocaleDateString()}
                    </div>
                  </div>
                  <div className="text-xs">
                    <RunStatusBadge runId={run.id} />
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-gray-500 text-sm">No runs started yet</div>
          )}
        </Card>

        {/* Top Solutions */}
        <Card 
          title="Top Solutions" 
          actions={
            <Button size="small" variant="secondary">
              View All
            </Button>
          }
        >
          {solutionsLoading ? (
            <LoadingSpinner size="medium" />
          ) : solutionsError ? (
            <ErrorMessage error={solutionsError} />
          ) : solutions.length > 0 ? (
            <div className="space-y-3">
              {solutions.map((solution, index) => (
                <div key={solution.id} className="flex justify-between items-center py-2 border-b border-gray-100 last:border-b-0">
                  <div>
                    <div className="font-medium text-sm">
                      #{index + 1} - {solution.id?.substring(0, 8)}...
                    </div>
                    <div className="text-xs text-gray-500">
                      {new Date(solution.dateCreated).toLocaleDateString()}
                    </div>
                  </div>
                  <div className="text-sm font-medium text-blue-600">
                    {solution.fitness?.score?.toFixed(3) || 'N/A'}
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-gray-500 text-sm">No solutions generated yet</div>
          )}
        </Card>

        {/* Recent Activity */}
        <Card 
          title="Recent Activity" 
          actions={
            <Button size="small" variant="secondary">
              View All
            </Button>
          }
        >
          {statesLoading ? (
            <LoadingSpinner size="medium" />
          ) : statesError ? (
            <ErrorMessage error={statesError} />
          ) : states.length > 0 ? (
            <div className="space-y-3 max-h-64 overflow-y-auto">
              {states.map((state, index) => (
                <div key={`${state.runId}-${index}`} className="flex justify-between items-start py-2 border-b border-gray-100 last:border-b-0">
                  <div>
                    <div className="font-medium text-sm">
                      Run {state.runId?.substring(0, 8)}...
                    </div>
                    <div className="text-xs text-gray-500">
                      {new Date(state.dateCreated).toLocaleString()}
                    </div>
                    {state.state?.generation && (
                      <div className="text-xs text-gray-400">
                        Gen: {state.state.generation}
                      </div>
                    )}
                  </div>
                  <div className="text-xs text-green-600">
                    Updated
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-gray-500 text-sm">No recent activity</div>
          )}
        </Card>
      </div>

      {/* Quick Actions */}
      <Card title="Quick Actions">
        <div className="flex flex-wrap gap-4">
          <Button variant="primary">
            Create New Problem
          </Button>
          <Button variant="secondary">
            View All Runs
          </Button>
          <Button variant="secondary">
            Browse Solutions
          </Button>
          <Button variant="secondary">
            System Status
          </Button>
        </div>
      </Card>
    </div>
  );
}

function StatCard({ title, value, loading, error, icon }) {
  return (
    <Card className="text-center">
      <div className="space-y-2">
        <div className="text-2xl">{icon}</div>
        <div className="text-2xl font-bold text-gray-900">
          {loading ? (
            <LoadingSpinner size="small" />
          ) : error ? (
            <span className="text-red-500">-</span>
          ) : (
            value.toLocaleString()
          )}
        </div>
        <div className="text-sm text-gray-600">{title}</div>
      </div>
    </Card>
  );
}

function RunStatusBadge({ runId }) {
  const { data: currentState, loading } = useApiData(
    () => apiServices.states.getStateByRunId(runId),
    [runId]
  );

  if (loading) {
    return <span className="text-gray-400">...</span>;
  }

  if (!currentState) {
    return <span className="text-gray-500">Unknown</span>;
  }

  // Simple status determination
  const stateAge = new Date() - new Date(currentState.dateCreated);
  const isRecent = stateAge < 5 * 60 * 1000; // 5 minutes

  if (isRecent) {
    return (
      <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-green-100 text-green-800">
        Running
      </span>
    );
  } else {
    return (
      <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800">
        Idle
      </span>
    );
  }
}
