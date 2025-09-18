import React, { useState } from 'react';
import { usePaginatedData, useApiData, useModal } from '../../hooks/index.jsx';
import { evolutionRunService, evolutionProblemService, evolutionStateService } from '../../services/index.jsx';
import { Card, Button, LoadingSpinner, ErrorMessage, EmptyState, Select } from '../common/index.jsx';
import RunDetails from './RunDetails.jsx';

export default function RunsList() {
  const [filterProblem, setFilterProblem] = useState('');
  const [selectedRun, setSelectedRun] = useState(null);
  const { isOpen: isDetailsOpen, open: openDetails, close: closeDetails } = useModal();

  // Fetch available problems for filtering
  const { data: problemsData } = useApiData(() => evolutionProblemService.getAll({ limit: 100 }));
  const problems = problemsData?.list || [];

  const {
    data: runs,
    totalCount,
    loading,
    error,
    refetch,
    currentPage,
    totalPages,
    hasNextPage,
    hasPrevPage,
    nextPage,
    prevPage,
    updateParams,
  } = usePaginatedData(evolutionRunService, {
    limit: 10,
    sort: 'dateCreated',
    order: 'desc',
  });

  const handleFilterChange = (problemId) => {
    setFilterProblem(problemId);
    updateParams({
      filters: problemId ? JSON.stringify({ problemId }) : undefined,
      offset: 0, // Reset to first page
    });
  };

  const handleViewDetails = (run) => {
    setSelectedRun(run);
    openDetails();
  };

  const handleStopRun = async (runId) => {
    if (!confirm('Are you sure you want to stop this run?')) return;
    
    try {
      await evolutionRunService.stopRun(runId);
      refetch();
    } catch (error) {
      alert('Failed to stop run: ' + error.message);
    }
  };

  const handleDeleteRun = async (runId) => {
    if (!confirm('Are you sure you want to delete this run? This action cannot be undone.')) return;
    
    try {
      await evolutionRunService.delete(runId);
      refetch();
    } catch (error) {
      alert('Failed to delete run: ' + error.message);
    }
  };

  if (loading && runs.length === 0) {
    return <LoadingSpinner size="large" className="py-12" />;
  }

  const problemOptions = [
    { value: '', label: 'All Problems' },
    ...problems.map(p => ({ value: p.id, label: p.name }))
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Evolution Runs</h1>
          <p className="text-gray-600">Monitor and manage your evolution runs</p>
        </div>
      </div>

      {/* Filters */}
      <Card>
        <div className="flex flex-col sm:flex-row gap-4">
          <div className="flex-1">
            <Select
              label="Filter by Problem"
              value={filterProblem}
              onChange={(e) => handleFilterChange(e.target.value)}
              options={problemOptions}
            />
          </div>
          <div className="flex items-center space-x-4 text-sm text-gray-600">
            <span>Total: {totalCount}</span>
            {loading && <LoadingSpinner size="small" />}
          </div>
        </div>
      </Card>

      {/* Error State */}
      {error && (
        <ErrorMessage error={error} onRetry={refetch} />
      )}

      {/* Empty State */}
      {!loading && runs.length === 0 && !error && (
        <Card>
          <EmptyState
            title="No runs found"
            description={filterProblem ? "No runs found for the selected problem." : "No evolution runs have been started yet."}
          />
        </Card>
      )}

      {/* Runs List */}
      {runs.length > 0 && (
        <div className="space-y-4">
          {runs.map((run) => (
            <RunCard
              key={run.id}
              run={run}
              problems={problems}
              onViewDetails={() => handleViewDetails(run)}
              onStop={() => handleStopRun(run.id)}
              onDelete={() => handleDeleteRun(run.id)}
            />
          ))}
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex justify-between items-center">
          <div className="text-sm text-gray-700">
            Page {currentPage + 1} of {totalPages}
          </div>
          <div className="flex space-x-2">
            <Button
              onClick={prevPage}
              disabled={!hasPrevPage}
              variant="secondary"
              size="small"
            >
              Previous
            </Button>
            <Button
              onClick={nextPage}
              disabled={!hasNextPage}
              variant="secondary"
              size="small"
            >
              Next
            </Button>
          </div>
        </div>
      )}

      {/* Run Details Modal */}
      {isDetailsOpen && selectedRun && (
        <RunDetails
          isOpen={isDetailsOpen}
          onClose={closeDetails}
          run={selectedRun}
          onUpdate={refetch}
        />
      )}
    </div>
  );
}

function RunCard({ run, problems, onViewDetails, onStop, onDelete }) {
  const problem = problems.find(p => p.id === run.problemId);
  
  // Get current state
  const { data: currentState, loading: stateLoading } = useApiData(
    () => evolutionStateService.getStateByRunId(run.id),
    [run.id]
  );

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

  const getStatusInfo = () => {
    if (stateLoading) {
      return { status: 'Loading...', color: 'gray' };
    }
    
    if (!currentState) {
      return { status: 'Unknown', color: 'gray' };
    }

    // Determine status based on state data
    // This is a simplified version - you might want to add more sophisticated logic
    const stateAge = new Date() - new Date(currentState.dateCreated);
    const isRecent = stateAge < 5 * 60 * 1000; // 5 minutes

    if (isRecent) {
      return { status: 'Running', color: 'green' };
    } else {
      return { status: 'Idle', color: 'yellow' };
    }
  };

  const statusInfo = getStatusInfo();

  return (
    <Card className="hover:shadow-md transition-shadow">
      <div className="flex justify-between items-start">
        <div className="flex-1 space-y-3">
          {/* Header */}
          <div className="flex justify-between items-start">
            <div>
              <h3 className="text-lg font-medium text-gray-900">
                Run {run.id?.substring(0, 8)}...
              </h3>
              <p className="text-sm text-gray-600">
                Problem: {problem ? problem.name : 'Unknown'}
              </p>
            </div>
            <div className="flex items-center space-x-2">
              <span className={`inline-flex items-center px-2 py-1 rounded-full text-xs font-medium
                ${statusInfo.color === 'green' ? 'bg-green-100 text-green-800' : 
                  statusInfo.color === 'yellow' ? 'bg-yellow-100 text-yellow-800' : 
                  'bg-gray-100 text-gray-800'}`}
              >
                {statusInfo.status}
              </span>
            </div>
          </div>

          {/* Run Information */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
            <div>
              <span className="text-gray-600">Started:</span>
              <div className="font-medium">
                {new Date(run.dateCreated).toLocaleDateString()}
              </div>
              <div className="text-xs text-gray-500">
                {new Date(run.dateCreated).toLocaleTimeString()}
              </div>
            </div>
            <div>
              <span className="text-gray-600">Duration:</span>
              <div className="font-medium">{formatDuration(run.dateCreated)}</div>
            </div>
            <div>
              <span className="text-gray-600">Iterations:</span>
              <div className="font-medium">
                {run.config?.mapelites?.numIterations || 'N/A'}
              </div>
            </div>
            <div>
              <span className="text-gray-600">Population:</span>
              <div className="font-medium">
                {run.config?.repository?.populationSize || 'N/A'}
              </div>
            </div>
          </div>

          {/* Current State Info */}
          {currentState && (
            <div className="bg-gray-50 p-3 rounded-md">
              <div className="text-xs text-gray-600 mb-1">Latest State:</div>
              <div className="text-sm">
                Last updated: {new Date(currentState.dateCreated).toLocaleString()}
              </div>
              {currentState.state && (
                <div className="text-xs text-gray-600 mt-1">
                  Generation: {currentState.state.generation || 'N/A'} | 
                  Best Fitness: {currentState.state.bestFitness || 'N/A'}
                </div>
              )}
            </div>
          )}
        </div>

        {/* Actions */}
        <div className="ml-6 flex flex-col space-y-2">
          <Button
            onClick={onViewDetails}
            variant="secondary"
            size="small"
          >
            View Details
          </Button>
          {statusInfo.status === 'Running' && (
            <Button
              onClick={onStop}
              variant="danger"
              size="small"
            >
              Stop
            </Button>
          )}
          <Button
            onClick={onDelete}
            variant="danger"
            size="small"
          >
            Delete
          </Button>
        </div>
      </div>
    </Card>
  );
}
