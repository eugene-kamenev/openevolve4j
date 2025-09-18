import React, { useState } from 'react';
import { useApiData } from '../../hooks/index.jsx';
import { evolutionSolutionService, evolutionRunService } from '../../services/index.jsx';
import { Modal, Button, Card, LoadingSpinner, ErrorMessage } from '../common/index.jsx';

export default function SolutionDetails({ isOpen, onClose, solution, onUpdate }) {
  const [activeTab, setActiveTab] = useState('overview');

  // Fetch related data
  const { data: run, loading: runLoading } = useApiData(
    () => evolutionRunService.getById(solution.runId),
    [solution.runId]
  );

  const { data: parents, loading: parentsLoading } = useApiData(
    () => evolutionSolutionService.getParents(solution.id),
    [solution.id]
  );

  const { data: childrenData, loading: childrenLoading } = useApiData(
    () => evolutionSolutionService.getSolutionsByParent(solution.id),
    [solution.id]
  );

  const children = childrenData?.list || [];

  const tabs = [
    { id: 'overview', label: 'Overview' },
    { id: 'solution', label: 'Solution Code' },
    { id: 'lineage', label: 'Lineage' },
    { id: 'fitness', label: 'Fitness Details' },
  ];

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`Solution ${solution.id?.substring(0, 12)}...`}
      maxWidth="max-w-6xl"
    >
      <div className="space-y-6">
        {/* Header */}
        <div className="flex justify-between items-center">
          <div className="text-sm text-gray-500">
            <div>Solution ID: {solution.id}</div>
            <div>Created: {new Date(solution.dateCreated).toLocaleString()}</div>
          </div>
          <div className="flex space-x-2">
            {solution.fitness?.score !== undefined && (
              <div className="text-lg font-bold text-blue-600">
                Score: {solution.fitness.score.toFixed(3)}
              </div>
            )}
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
              solution={solution}
              run={run}
              runLoading={runLoading}
              parents={parents}
              children={children}
            />
          )}
          {activeTab === 'solution' && (
            <SolutionTab solution={solution} />
          )}
          {activeTab === 'lineage' && (
            <LineageTab
              solution={solution}
              parents={parents}
              parentsLoading={parentsLoading}
              children={children}
              childrenLoading={childrenLoading}
            />
          )}
          {activeTab === 'fitness' && (
            <FitnessTab solution={solution} />
          )}
        </div>
      </div>
    </Modal>
  );
}

function OverviewTab({ solution, run, runLoading, parents, children }) {
  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
      {/* Basic Information */}
      <Card title="Basic Information">
        <div className="space-y-3">
          <InfoRow label="Solution ID" value={solution.id} />
          <InfoRow label="Created" value={new Date(solution.dateCreated).toLocaleString()} />
          <InfoRow
            label="Run"
            value={runLoading ? 'Loading...' : (run ? `${run.id?.substring(0, 8)}...` : 'Unknown')}
          />
          <InfoRow
            label="Parent"
            value={solution.parentId ? `${solution.parentId.substring(0, 8)}...` : 'Root'}
          />
          <InfoRow label="Children" value={children.length} />
          <InfoRow label="Ancestors" value={parents?.length || 0} />
        </div>
      </Card>

      {/* Fitness Summary */}
      <Card title="Fitness Summary">
        {solution.fitness ? (
          <div className="space-y-3">
            {Object.entries(solution.fitness).map(([key, value]) => (
              <InfoRow
                key={key}
                label={key.charAt(0).toUpperCase() + key.slice(1)}
                value={typeof value === 'number' ? value.toFixed(4) : value}
              />
            ))}
          </div>
        ) : (
          <div className="text-gray-500 text-sm">No fitness data available</div>
        )}
      </Card>

      {/* Metadata */}
      {solution.metadata && (
        <Card title="Metadata">
          <div className="space-y-3">
            {Object.entries(solution.metadata).map(([key, value]) => (
              <InfoRow
                key={key}
                label={key}
                value={typeof value === 'object' ? JSON.stringify(value) : value}
              />
            ))}
          </div>
        </Card>
      )}

      {/* Run Information */}
      {run && (
        <Card title="Run Information">
          <div className="space-y-3">
            <InfoRow label="Run ID" value={run.id} />
            <InfoRow label="Problem ID" value={run.problemId} />
            <InfoRow label="Run Started" value={new Date(run.dateCreated).toLocaleString()} />
            <InfoRow
              label="Population Size"
              value={run.config?.repository?.populationSize || 'N/A'}
            />
          </div>
        </Card>
      )}
    </div>
  );
}

function SolutionTab({ solution }) {
  const solutionCode = solution.solution;

  return (
    <Card title="Solution Code">
      {solutionCode ? (
        <div className="space-y-4">
          {/* Code files */}
          {solutionCode.files && Object.keys(solutionCode.files).length > 0 ? (
            <div className="space-y-4">
              {Object.entries(solutionCode.files).map(([filename, content]) => (
                <div key={filename} className="border border-gray-200 rounded-md">
                  <div className="bg-gray-50 px-4 py-2 border-b border-gray-200">
                    <h4 className="text-sm font-medium text-gray-900">{filename}</h4>
                  </div>
                  <div className="p-4">
                    <pre className="bg-gray-50 p-4 rounded-md text-sm overflow-auto max-h-96 whitespace-pre-wrap">
                      {content}
                    </pre>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <div className="bg-gray-50 p-4 rounded-md">
              <pre className="text-sm overflow-auto max-h-96 whitespace-pre-wrap">
                {typeof solutionCode === 'string' ? solutionCode : JSON.stringify(solutionCode, null, 2)}
              </pre>
            </div>
          )}
        </div>
      ) : (
        <div className="text-gray-500 text-sm">No solution code available</div>
      )}
    </Card>
  );
}

function LineageTab({ solution, parents, parentsLoading, children, childrenLoading }) {
  return (
    <div className="space-y-6">
      {/* Parents/Ancestors */}
      <Card title="Ancestry">
        {parentsLoading ? (
          <LoadingSpinner size="medium" />
        ) : parents && parents.length > 0 ? (
          <div className="space-y-3">
            <div className="text-sm text-gray-600 mb-3">
              This solution has {parents.length} ancestor(s):
            </div>
            {parents.map((parent, index) => (
              <div key={parent.id} className="border border-gray-200 rounded-md p-3">
                <div className="flex justify-between items-start">
                  <div>
                    <div className="font-medium text-sm">
                      Generation -{parents.length - index}: {parent.id?.substring(0, 12)}...
                    </div>
                    <div className="text-xs text-gray-500">
                      Created: {new Date(parent.dateCreated).toLocaleString()}
                    </div>
                  </div>
                  <div className="text-sm">
                    {parent.fitness?.score !== undefined && (
                      <span className="font-medium">
                        Score: {parent.fitness.score.toFixed(3)}
                      </span>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="text-gray-500 text-sm">This is a root solution (no parents)</div>
        )}
      </Card>

      {/* Children/Descendants */}
      <Card title="Descendants">
        {childrenLoading ? (
          <LoadingSpinner size="medium" />
        ) : children && children.length > 0 ? (
          <div className="space-y-3">
            <div className="text-sm text-gray-600 mb-3">
              This solution has {children.length} direct descendant(s):
            </div>
            {children.map((child) => (
              <div key={child.id} className="border border-gray-200 rounded-md p-3">
                <div className="flex justify-between items-start">
                  <div>
                    <div className="font-medium text-sm">
                      {child.id?.substring(0, 12)}...
                    </div>
                    <div className="text-xs text-gray-500">
                      Created: {new Date(child.dateCreated).toLocaleString()}
                    </div>
                  </div>
                  <div className="text-sm">
                    {child.fitness?.score !== undefined && (
                      <span className="font-medium">
                        Score: {child.fitness.score.toFixed(3)}
                      </span>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="text-gray-500 text-sm">This solution has no descendants yet</div>
        )}
      </Card>
    </div>
  );
}

function FitnessTab({ solution }) {
  return (
    <div className="space-y-6">
      {/* Detailed Fitness */}
      <Card title="Fitness Breakdown">
        {solution.fitness ? (
          <div className="space-y-4">
            {Object.entries(solution.fitness).map(([metric, value]) => (
              <div key={metric} className="border border-gray-200 rounded-md p-4">
                <div className="flex justify-between items-center mb-2">
                  <h4 className="font-medium text-gray-900 capitalize">{metric}</h4>
                  <span className="text-lg font-bold text-blue-600">
                    {typeof value === 'number' ? value.toFixed(4) : value}
                  </span>
                </div>
                
                {typeof value === 'number' && (
                  <div className="w-full bg-gray-200 rounded-full h-2">
                    <div
                      className="bg-blue-600 h-2 rounded-full"
                      style={{ width: `${Math.min(100, Math.max(0, value * 100))}%` }}
                    ></div>
                  </div>
                )}
              </div>
            ))}
          </div>
        ) : (
          <div className="text-gray-500 text-sm">No fitness data available</div>
        )}
      </Card>

      {/* Metadata Details */}
      {solution.metadata && (
        <Card title="Detailed Metadata">
          <pre className="bg-gray-50 p-4 rounded-md text-sm overflow-auto max-h-96">
            {JSON.stringify(solution.metadata, null, 2)}
          </pre>
        </Card>
      )}
    </div>
  );
}

function InfoRow({ label, value }) {
  return (
    <div className="flex justify-between">
      <span className="text-gray-600 text-sm">{label}:</span>
      <span className="font-medium text-sm text-right break-all">{value}</span>
    </div>
  );
}
