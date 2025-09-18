import React, { useState } from 'react';
import { useApiData, useModal } from '../../hooks/index.jsx';
import { evolutionRunService } from '../../services/index.jsx';
import { Modal, Button, Card, LoadingSpinner, ErrorMessage, EmptyState } from '../common/index.jsx';
import ProblemForm from './ProblemForm.jsx';

export default function ProblemDetails({ isOpen, onClose, problem, onUpdate }) {
  const [activeTab, setActiveTab] = useState('overview');
  const { isOpen: isEditOpen, open: openEdit, close: closeEdit } = useModal();

  const {
    data: relatedRuns,
    loading: runsLoading,
    error: runsError,
    refetch: refetchRuns,
  } = useApiData(() => evolutionRunService.getRunsByProblem(problem.id), [problem.id]);

  const handleEdit = () => {
    openEdit();
  };

  const handleEditSuccess = () => {
    closeEdit();
    onUpdate();
  };

  const handleStartRun = async () => {
    try {
      await evolutionRunService.startRun(problem.id);
      refetchRuns();
      alert('Evolution run started successfully!');
    } catch (error) {
      alert('Failed to start run: ' + error.message);
    }
  };

  const tabs = [
    { id: 'overview', label: 'Overview' },
    { id: 'configuration', label: 'Configuration' },
    { id: 'runs', label: 'Related Runs' },
  ];

  return (
    <>
      <Modal 
        isOpen={isOpen} 
        onClose={onClose} 
        title={problem.name}
        maxWidth="max-w-6xl"
      >
        <div className="space-y-6">
          {/* Header Actions */}
          <div className="flex justify-between items-center">
            <div className="text-sm text-gray-500">
              ID: {problem.id}
            </div>
            <div className="flex space-x-2">
              <Button onClick={handleStartRun} variant="success" size="small">
                Start Run
              </Button>
              <Button onClick={handleEdit} variant="secondary" size="small">
                Edit
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
              <OverviewTab problem={problem} />
            )}
            {activeTab === 'configuration' && (
              <ConfigurationTab config={problem.config} />
            )}
            {activeTab === 'runs' && (
              <RunsTab 
                runs={relatedRuns?.list || []} 
                loading={runsLoading} 
                error={runsError}
                onRefresh={refetchRuns}
              />
            )}
          </div>
        </div>
      </Modal>

      {/* Edit Modal */}
      {isEditOpen && (
        <ProblemForm
          isOpen={isEditOpen}
          onClose={closeEdit}
          onSuccess={handleEditSuccess}
          problem={problem}
        />
      )}
    </>
  );
}

function OverviewTab({ problem }) {
  const { config } = problem;

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
      {/* Basic Information */}
      <Card title="Basic Information">
        <div className="space-y-3">
          <InfoRow label="Name" value={problem.name} />
          <InfoRow label="ID" value={problem.id} />
          <InfoRow label="Language" value={config?.solution?.language || 'python'} />
          <InfoRow label="File Pattern" value={config?.solution?.pattern || '.*\\.py$'} />
        </div>
      </Card>

      {/* MAP-Elites Summary */}
      <Card title="MAP-Elites Summary">
        <div className="space-y-3">
          <InfoRow label="Iterations" value={config?.mapelites?.numIterations || 100} />
          <InfoRow label="Bins" value={config?.mapelites?.bins || 10} />
          <InfoRow label="Dimensions" value={config?.mapelites?.dimensions?.length || 3} />
          <InfoRow 
            label="Dimension Names" 
            value={config?.mapelites?.dimensions?.join(', ') || 'score, complexity, diversity'} 
          />
        </div>
      </Card>

      {/* Repository Summary */}
      <Card title="Repository Configuration">
        <div className="space-y-3">
          <InfoRow label="Population Size" value={config?.repository?.populationSize || 50} />
          <InfoRow label="Archive Size" value={config?.repository?.archiveSize || 10} />
          <InfoRow label="Islands" value={config?.repository?.islands || 2} />
          <InfoRow label="Checkpoint Interval" value={config?.repository?.checkpointInterval || 10} />
        </div>
      </Card>

      {/* Metrics */}
      <Card title="Enabled Metrics">
        <div className="flex flex-wrap gap-2">
          {config?.metrics && Object.entries(config.metrics).map(([metric, enabled]) => (
            <span
              key={metric}
              className={`inline-flex items-center px-2 py-1 rounded-full text-xs font-medium ${
                enabled 
                  ? 'bg-green-100 text-green-800'
                  : 'bg-gray-100 text-gray-800'
              }`}
            >
              {metric} {enabled ? '✓' : '✗'}
            </span>
          ))}
        </div>
      </Card>
    </div>
  );
}

function ConfigurationTab({ config }) {
  return (
    <div className="space-y-6">
      {/* JSON View */}
      <Card title="Full Configuration">
        <pre className="bg-gray-50 p-4 rounded-md text-sm overflow-auto max-h-96">
          {JSON.stringify(config, null, 2)}
        </pre>
      </Card>
    </div>
  );
}

function RunsTab({ runs, loading, error, onRefresh }) {
  if (loading) {
    return <LoadingSpinner size="large" className="py-12" />;
  }

  if (error) {
    return <ErrorMessage error={error} onRetry={onRefresh} />;
  }

  if (!runs || runs.length === 0) {
    return (
      <EmptyState
        title="No runs found"
        description="This problem hasn't been used in any evolution runs yet."
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
        <h4 className="text-lg font-medium">Related Runs ({runs.length})</h4>
        <Button onClick={onRefresh} variant="secondary" size="small">
          Refresh
        </Button>
      </div>

      <div className="space-y-3">
        {runs.map((run) => (
          <div
            key={run.id}
            className="border border-gray-200 rounded-md p-4 hover:bg-gray-50"
          >
            <div className="flex justify-between items-start">
              <div>
                <div className="font-medium text-gray-900">
                  Run {run.id?.substring(0, 8)}...
                </div>
                <div className="text-sm text-gray-500">
                  Created: {new Date(run.dateCreated).toLocaleString()}
                </div>
              </div>
              <div className="text-sm">
                <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                  Active
                </span>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function InfoRow({ label, value }) {
  return (
    <div className="flex justify-between">
      <span className="text-gray-600 text-sm">{label}:</span>
      <span className="font-medium text-sm">{value}</span>
    </div>
  );
}
