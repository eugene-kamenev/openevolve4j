import React, { useEffect, useMemo, useState } from 'react';
import { Database, Hash, Calendar, TrendingUp } from 'lucide-react';
import { RunsApi, SolutionsApi, ModelsApi } from '../services/api';
import { formatScore } from '../utils/formatters';

/**
 * Reusable browser for solutions with run/model filters and sortable table.
 * Can operate in selection mode to pick solutions (checkboxes per row).
 */
const SolutionsBrowser = ({
  problemId,
  metrics,
  selectionMode = false,
  selectedIds = [],
  onChangeSelectedIds = () => {},
  selectViableOnly = true,
  onRowClick,
  highlightIds = [],
  renderActions,
  onSolutionsLoaded,
}) => {
  const [runs, setRuns] = useState([]);
  const [selectedRunId, setSelectedRunId] = useState(null);
  const [models, setModels] = useState([]);
  const [selectedModel, setSelectedModel] = useState('');
  const [solutions, setSolutions] = useState([]);
  const [loadingRuns, setLoadingRuns] = useState(false);
  const [loadingSolutions, setLoadingSolutions] = useState(false);
  const [loadingModels, setLoadingModels] = useState(false);
  const [sortField, setSortField] = useState('dateCreated');
  const [sortDirection, setSortDirection] = useState('desc');

  useEffect(() => {
    let cancelled = false;
    const loadRuns = async () => {
      if (!problemId) return;
      setLoadingRuns(true);
      try {
        const resp = await RunsApi.list({
          filters: { forProblem: problemId },
          sort: 'dateCreated',
          order: 'desc',
          limit: 100,
          offset: 0,
        });
        if (cancelled) return;
        const list = resp?.list ?? [];
        setRuns(list);
        setSelectedRunId(list.length > 0 ? list[0].id : null);
      } catch (_) {
        if (!cancelled) {
          setRuns([]);
          setSelectedRunId(null);
        }
      } finally {
        if (!cancelled) setLoadingRuns(false);
      }
    };
    const loadModels = async () => {
      setLoadingModels(true);
      try {
        const resp = await ModelsApi.list({ limit: 200, offset: 0, sort: 'name', order: 'asc' });
        if (!cancelled) setModels(resp?.list || resp || []);
      } catch (_) {
        if (!cancelled) setModels([]);
      } finally {
        if (!cancelled) setLoadingModels(false);
      }
    };
    loadRuns();
    loadModels();
    return () => { cancelled = true; };
  }, [problemId]);

  useEffect(() => {
    let cancelled = false;
    const loadSolutionsForRun = async (runId) => {
      if (!runId) {
        setSolutions([]);
        return;
      }
      setLoadingSolutions(true);
      try {
        const filters = selectedModel ? { forLLMModel: selectedModel } : {};
        const resp = await SolutionsApi.listForRun(runId, {
          limit: 500,
          offset: 0,
          sort: 'dateCreated',
          order: 'desc',
          filters,
        });
        if (cancelled) return;
        const list = resp?.list ?? [];
        setSolutions(list);
        if (onSolutionsLoaded) onSolutionsLoaded(list);
      } catch (_) {
        if (!cancelled) {
          setSolutions([]);
          if (onSolutionsLoaded) onSolutionsLoaded([]);
        }
      } finally {
        if (!cancelled) setLoadingSolutions(false);
      }
    };
    if (selectedRunId) loadSolutionsForRun(selectedRunId);
    else {
      setSolutions([]);
      if (onSolutionsLoaded) onSolutionsLoaded([]);
    }
    return () => { cancelled = true; };
  }, [selectedRunId, selectedModel, onSolutionsLoaded]);

  const handleSort = (field) => {
    if (sortField === field) setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc');
    else { setSortField(field); setSortDirection('desc'); }
  };

  const sortedSolutions = useMemo(() => {
    const arr = [...solutions];
    arr.sort((a, b) => {
      let aValue = a[sortField];
      let bValue = b[sortField];
      if (sortField === 'score') {
        aValue = a.fitness?.score || 0;
        bValue = b.fitness?.score || 0;
      } else if (sortField === 'dateCreated') {
        aValue = a.dateCreated || '';
        bValue = b.dateCreated || '';
      } else if (sortField === 'llmModel') {
        aValue = (a.solution?.metadata?.llmModel || '').toLowerCase();
        bValue = (b.solution?.metadata?.llmModel || '').toLowerCase();
      }
      if (aValue < bValue) return sortDirection === 'asc' ? -1 : 1;
      if (aValue > bValue) return sortDirection === 'asc' ? 1 : -1;
      return 0;
    });
    return arr;
  }, [solutions, sortField, sortDirection]);

  const bestSolutionForDisplay = useMemo(() => {
    if (!Array.isArray(solutions) || solutions.length === 0) return null;
    const metricKeys = metrics ? Object.keys(metrics) : [];
    const key = metricKeys[0] || 'score';
    return solutions.reduce((best, cur) => {
      const b = best?.fitness?.[key] ?? best?.fitness?.score;
      const c = cur?.fitness?.[key] ?? cur?.fitness?.score;
      if (b == null) return cur;
      if (c == null) return best;
      return c > b ? cur : best;
    }, null);
  }, [solutions, metrics]);

  const formatDate = (dateString) => {
    if (!dateString) return '-';
    try { return new Date(dateString).toLocaleString(); } catch { return String(dateString); }
  };

  const isViable = (sol) => !!(sol?.solution?.files && Object.keys(sol.solution.files).length > 0);

  const toggleSelection = (id) => {
    const next = selectedIds.includes(id)
      ? selectedIds.filter(x => x !== id)
      : [...selectedIds, id];
    onChangeSelectedIds(next);
  };

  return (
    <div className="solutions-browser">
      <div className="solutions-header">
        <div className="header-content">
          <div className="header-info">
            <h3><Database size={20} /> Solutions</h3>
            <p className="text-dim">Browse solutions by run and model</p>
          </div>
          <div className="header-actions">
            <label htmlFor="run-select" className="text-dim" style={{ marginRight: 8 }}>Run:</label>
            <select
              id="run-select"
              className="oe-select"
              value={selectedRunId || ''}
              onChange={(e) => setSelectedRunId(e.target.value || null)}
              disabled={loadingRuns}
            >
              {runs.length === 0 && <option value="">No runs</option>}
              {runs.map((run) => (
                <option key={run.id} value={run.id}>
                  {run.id?.substring(0, 8)} • {formatDate(run.dateCreated)}
                </option>
              ))}
            </select>

            <label htmlFor="model-select" className="text-dim" style={{ marginLeft: 12, marginRight: 8 }}>Model:</label>
            <select
              id="model-select"
              className="oe-select"
              value={selectedModel}
              onChange={(e) => setSelectedModel(e.target.value)}
              disabled={loadingModels}
            >
              <option value="">All models</option>
              {models.map((m) => (
                <option key={m.id || m.name || m.model} value={m.name || m.model || ''}>
                  {m.name || m.model}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="solutions-stats">
          <div className="stat-item">
            <div className="stat-value">{solutions.length}{loadingSolutions ? '…' : ''}</div>
            <div className="stat-label">Total Solutions</div>
          </div>
          <div className="stat-item">
            <div className="stat-value">
              {bestSolutionForDisplay
                ? `${bestSolutionForDisplay.id?.substring(0, 8) || 'unknown'}: ${formatScore(bestSolutionForDisplay.fitness, metrics)}`
                : (solutions.length > 0 ? 'No best solution' : '-')}
            </div>
            <div className="stat-label">Best Score</div>
          </div>
        </div>
      </div>

      <div className="solutions-table-container">
        {solutions.length === 0 ? (
          <div className="empty-state">
            <Database size={48} />
            <h4>No Solutions Yet</h4>
            <p className="text-dim">{runs.length === 0 ? 'Create and run evolution to generate solutions' : 'No solutions found for the selected run'}</p>
          </div>
        ) : (
          <div className="solutions-table-wrapper">
            <table className="solutions-table">
              <thead>
                <tr>
                  {selectionMode && <th style={{ width: 36 }}></th>}
                  <th className={`sortable ${sortField === 'id' ? sortDirection : ''}`} onClick={() => handleSort('id')}>
                    <Hash size={14} /> ID
                  </th>
                  <th className={`sortable ${sortField === 'score' ? sortDirection : ''}`}>
                    <TrendingUp size={14} /> Score {metrics ? `(${Object.keys(metrics).join(', ')})` : ''}
                  </th>
                  <th className={`sortable ${sortField === 'llmModel' ? sortDirection : ''}`} onClick={() => handleSort('llmModel')}>
                    Model
                  </th>
                  <th className={`sortable ${sortField === 'dateCreated' ? sortDirection : ''}`} onClick={() => handleSort('dateCreated')}>
                    <Calendar size={14} /> Created
                  </th>
                  {renderActions && <th>Actions</th>}
                </tr>
              </thead>
              <tbody>
                {sortedSolutions.map((solution) => {
                  const viable = isViable(solution);
                  const disabled = selectionMode && selectViableOnly && !viable;
                  const checked = selectedIds.includes(solution.id);
                  const isHighlighted = highlightIds.includes(solution.id);
                  return (
                    <tr
                      key={solution.id}
                      className={isHighlighted || checked ? 'selected' : ''}
                      onClick={onRowClick ? () => onRowClick(solution) : undefined}
                      style={{ cursor: onRowClick ? 'pointer' : 'default' }}
                    >
                      {selectionMode && (
                        <td>
                          <input
                            type="checkbox"
                            checked={checked}
                            onChange={() => toggleSelection(solution.id)}
                            disabled={disabled}
                            title={disabled ? 'Solution has no files' : ''}
                          />
                        </td>
                      )}
                      <td className="solution-id"><code>{solution.id?.substring(0, 8) || '-'}</code></td>
                      <td className="score"><span className="score-value">{formatScore(solution.fitness, metrics)}</span></td>
                      <td className="model"><span>{solution.solution?.metadata?.llmModel || '-'}</span></td>
                      <td className="created"><span className="date-text">{formatDate(solution.dateCreated)}</span></td>
                      {renderActions && (
                        <td className="actions" onClick={(e) => e.stopPropagation()}>
                          {renderActions(solution)}
                        </td>
                      )}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};

export default SolutionsBrowser;
