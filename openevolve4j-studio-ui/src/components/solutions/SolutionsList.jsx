import React, { useState } from 'react';
import { usePaginatedData, useApiData, useModal } from '../../hooks/index.jsx';
import { evolutionSolutionService, evolutionRunService } from '../../services/index.jsx';
import { Card, Button, LoadingSpinner, ErrorMessage, EmptyState, Select, Input } from '../common/index.jsx';
import SolutionDetails from './SolutionDetails.jsx';

export default function SolutionsList() {
  const [filterRun, setFilterRun] = useState('');
  const [sortMetric, setSortMetric] = useState('dateCreated');
  const [selectedSolution, setSelectedSolution] = useState(null);
  const { isOpen: isDetailsOpen, open: openDetails, close: closeDetails } = useModal();

  // Fetch available runs for filtering
  const { data: runsData } = useApiData(() => evolutionRunService.getAll({ limit: 100 }));
  const runs = runsData?.list || [];

  const {
    data: solutions,
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
  } = usePaginatedData(evolutionSolutionService, {
    limit: 12,
    sort: 'dateCreated',
    order: 'desc',
  });

  const handleFilterChange = (runId) => {
    setFilterRun(runId);
    updateParams({
      filters: runId ? JSON.stringify({ runId }) : undefined,
      offset: 0, // Reset to first page
    });
  };

  const handleSortChange = (metric) => {
    setSortMetric(metric);
    updateParams({
      sort: metric,
      order: metric === 'dateCreated' ? 'desc' : 'desc', // Most metrics should be descending
      offset: 0,
    });
  };

  const handleViewDetails = (solution) => {
    setSelectedSolution(solution);
    openDetails();
  };

  const handleDeleteSolution = async (solutionId) => {
    if (!confirm('Are you sure you want to delete this solution?')) return;
    
    try {
      await evolutionSolutionService.delete(solutionId);
      refetch();
    } catch (error) {
      alert('Failed to delete solution: ' + error.message);
    }
  };

  if (loading && solutions.length === 0) {
    return <LoadingSpinner size="large" className="py-12" />;
  }

  const runOptions = [
    { value: '', label: 'All Runs' },
    ...runs.map(r => ({ 
      value: r.id, 
      label: `Run ${r.id?.substring(0, 8)}... (${new Date(r.dateCreated).toLocaleDateString()})` 
    }))
  ];

  const sortOptions = [
    { value: 'dateCreated', label: 'Date Created' },
    { value: 'fitness.score', label: 'Score' },
    { value: 'fitness.complexity', label: 'Complexity' },
    { value: 'fitness.diversity', label: 'Diversity' },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Evolution Solutions</h1>
          <p className="text-gray-600">Browse and analyze generated solutions</p>
        </div>
      </div>

      {/* Filters and Sort */}
      <Card>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <Select
            label="Filter by Run"
            value={filterRun}
            onChange={(e) => handleFilterChange(e.target.value)}
            options={runOptions}
          />
          <Select
            label="Sort by"
            value={sortMetric}
            onChange={(e) => handleSortChange(e.target.value)}
            options={sortOptions}
          />
          <div className="flex items-center space-x-4 text-sm text-gray-600 md:justify-end">
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
      {!loading && solutions.length === 0 && !error && (
        <Card>
          <EmptyState
            title="No solutions found"
            description={filterRun ? "No solutions found for the selected run." : "No solutions have been generated yet."}
          />
        </Card>
      )}

      {/* Solutions Grid */}
      {solutions.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {solutions.map((solution) => (
            <SolutionCard
              key={solution.id}
              solution={solution}
              runs={runs}
              onViewDetails={() => handleViewDetails(solution)}
              onDelete={() => handleDeleteSolution(solution.id)}
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

      {/* Solution Details Modal */}
      {isDetailsOpen && selectedSolution && (
        <SolutionDetails
          isOpen={isDetailsOpen}
          onClose={closeDetails}
          solution={selectedSolution}
          onUpdate={refetch}
        />
      )}
    </div>
  );
}

function SolutionCard({ solution, runs, onViewDetails, onDelete }) {
  const run = runs.find(r => r.id === solution.runId);
  
  const formatFitness = (fitness) => {
    if (!fitness) return null;
    
    return Object.entries(fitness).map(([key, value]) => ({
      key,
      value: typeof value === 'number' ? value.toFixed(3) : value
    }));
  };

  const fitnessEntries = formatFitness(solution.fitness);
  const overallScore = solution.fitness?.score;

  const getScoreColor = (score) => {
    if (score >= 0.8) return 'text-green-600';
    if (score >= 0.6) return 'text-yellow-600';
    if (score >= 0.4) return 'text-orange-600';
    return 'text-red-600';
  };

  return (
    <Card className="hover:shadow-md transition-shadow">
      <div className="space-y-4">
        {/* Header */}
        <div className="flex justify-between items-start">
          <div>
            <h3 className="text-lg font-medium text-gray-900 truncate">
              Solution {solution.id?.substring(0, 12)}...
            </h3>
            <p className="text-sm text-gray-500">
              Run: {run ? `${run.id?.substring(0, 8)}...` : 'Unknown'}
            </p>
          </div>
          {overallScore !== undefined && (
            <div className={`text-lg font-bold ${getScoreColor(overallScore)}`}>
              {overallScore.toFixed(3)}
            </div>
          )}
        </div>

        {/* Metadata */}
        <div className="space-y-2">
          <div className="flex justify-between text-sm">
            <span className="text-gray-600">Created:</span>
            <span className="font-medium">
              {new Date(solution.dateCreated).toLocaleDateString()}
            </span>
          </div>
          <div className="flex justify-between text-sm">
            <span className="text-gray-600">Parent:</span>
            <span className="font-medium">
              {solution.parentId ? `${solution.parentId.substring(0, 8)}...` : 'Root'}
            </span>
          </div>
        </div>

        {/* Fitness Metrics */}
        {fitnessEntries && fitnessEntries.length > 0 && (
          <div className="space-y-1">
            <div className="text-sm font-medium text-gray-700">Fitness Metrics:</div>
            {fitnessEntries.map(({ key, value }) => (
              <div key={key} className="flex justify-between text-sm">
                <span className="text-gray-600 capitalize">{key}:</span>
                <span className="font-medium">{value}</span>
              </div>
            ))}
          </div>
        )}

        {/* Metadata */}
        {solution.metadata && (
          <div className="bg-gray-50 p-2 rounded-md">
            <div className="text-xs text-gray-600 mb-1">Metadata:</div>
            <div className="text-xs">
              {Object.entries(solution.metadata).map(([key, value]) => (
                <div key={key} className="flex justify-between">
                  <span>{key}:</span>
                  <span>{typeof value === 'object' ? JSON.stringify(value) : value}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Actions */}
        <div className="flex justify-between">
          <Button
            onClick={onViewDetails}
            variant="secondary"
            size="small"
          >
            View Details
          </Button>
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
