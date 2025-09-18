import React, { useState } from 'react';
import { usePaginatedData, useModal } from '../../hooks/index.jsx';
import { evolutionProblemService } from '../../services/index.jsx';
import { Card, Button, LoadingSpinner, ErrorMessage, EmptyState, Input } from '../common/index.jsx';
import ProblemForm from './ProblemForm.jsx';
import ProblemDetails from './ProblemDetails.jsx';

export default function ProblemsList() {
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedProblem, setSelectedProblem] = useState(null);
  const { isOpen: isFormOpen, open: openForm, close: closeForm } = useModal();
  const { isOpen: isDetailsOpen, open: openDetails, close: closeDetails } = useModal();

  const {
    data: problems,
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
  } = usePaginatedData(evolutionProblemService, {
    limit: 12,
    sort: 'name',
    order: 'asc',
  });

  const handleSearch = (value) => {
    setSearchTerm(value);
    updateParams({
      filters: value ? JSON.stringify({ name: { $like: `%${value}%` } }) : undefined,
      offset: 0, // Reset to first page
    });
  };

  const handleViewDetails = (problem) => {
    setSelectedProblem(problem);
    openDetails();
  };

  const handleProblemCreated = () => {
    closeForm();
    refetch();
  };

  const handleDeleteProblem = async (problemId) => {
    if (!confirm('Are you sure you want to delete this problem?')) return;
    
    try {
      await evolutionProblemService.delete(problemId);
      refetch();
    } catch (error) {
      alert('Failed to delete problem: ' + error.message);
    }
  };

  if (loading && problems.length === 0) {
    return <LoadingSpinner size="large" className="py-12" />;
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Evolution Problems</h1>
          <p className="text-gray-600">Manage your evolution problem configurations</p>
        </div>
        <Button onClick={openForm} variant="primary">
          Create Problem
        </Button>
      </div>

      {/* Search and Filters */}
      <Card>
        <div className="flex flex-col sm:flex-row gap-4">
          <div className="flex-1">
            <Input
              placeholder="Search problems..."
              value={searchTerm}
              onChange={(e) => handleSearch(e.target.value)}
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
      {!loading && problems.length === 0 && !error && (
        <Card>
          <EmptyState
            title="No problems found"
            description={searchTerm ? "No problems match your search criteria." : "Get started by creating your first evolution problem."}
            action={
              !searchTerm && (
                <Button onClick={openForm} variant="primary">
                  Create Problem
                </Button>
              )
            }
          />
        </Card>
      )}

      {/* Problems Grid */}
      {problems.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {problems.map((problem) => (
            <ProblemCard
              key={problem.id}
              problem={problem}
              onViewDetails={() => handleViewDetails(problem)}
              onDelete={() => handleDeleteProblem(problem.id)}
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

      {/* Create Problem Modal */}
      {isFormOpen && (
        <ProblemForm
          isOpen={isFormOpen}
          onClose={closeForm}
          onSuccess={handleProblemCreated}
        />
      )}

      {/* Problem Details Modal */}
      {isDetailsOpen && selectedProblem && (
        <ProblemDetails
          isOpen={isDetailsOpen}
          onClose={closeDetails}
          problem={selectedProblem}
          onUpdate={refetch}
        />
      )}
    </div>
  );
}

function ProblemCard({ problem, onViewDetails, onDelete }) {
  const { config } = problem;
  
  return (
    <Card className="hover:shadow-md transition-shadow">
      <div className="space-y-4">
        {/* Header */}
        <div>
          <h3 className="text-lg font-medium text-gray-900 truncate">
            {problem.name}
          </h3>
          <p className="text-sm text-gray-500">
            ID: {problem.id?.substring(0, 8)}...
          </p>
        </div>

        {/* Config Summary */}
        <div className="space-y-2">
          <div className="flex justify-between text-sm">
            <span className="text-gray-600">Language:</span>
            <span className="font-medium">{config?.solution?.language || 'python'}</span>
          </div>
          <div className="flex justify-between text-sm">
            <span className="text-gray-600">Iterations:</span>
            <span className="font-medium">{config?.mapelites?.numIterations || 100}</span>
          </div>
          <div className="flex justify-between text-sm">
            <span className="text-gray-600">Population:</span>
            <span className="font-medium">{config?.repository?.populationSize || 50}</span>
          </div>
          <div className="flex justify-between text-sm">
            <span className="text-gray-600">Dimensions:</span>
            <span className="font-medium">
              {config?.mapelites?.dimensions?.length || 3}
            </span>
          </div>
        </div>

        {/* Metrics */}
        {config?.metrics && (
          <div className="flex flex-wrap gap-1">
            {Object.keys(config.metrics).map((metric) => (
              <span
                key={metric}
                className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-blue-100 text-blue-800"
              >
                {metric}
              </span>
            ))}
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
