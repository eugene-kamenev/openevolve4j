import { useCallback, useEffect, useMemo, useState } from 'react';
import { FileJson, Play, Plus, RefreshCcw, StopCircle, Trash, PenSquare } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import Modal from '../components/Modal.jsx';
import { LoadingState, ErrorState, EmptyState } from '../components/States.jsx';
import { StatusBadge } from '../components/StatusBadge.jsx';
import { usePageMeta } from '../hooks/usePageMeta.js';
import {
  createProblem,
  deleteProblem,
  fetchProblemStatuses,
  fetchProblems,
  startProblem,
  stopProblem,
  updateProblem
} from '../services/problems.js';
import { fetchModels } from '../services/models.js';
import { createDefaultPuctTreeConfig } from '../Entity.js';

const schema = yup.object({
  name: yup.string().required('A descriptive name is required')
});

const DEFAULT_CONFIG = createDefaultPuctTreeConfig();

const TABS = [
  { key: 'general', label: 'General' },
  { key: 'metrics', label: 'Metrics' },
  { key: 'llm', label: 'LLM models' }
];

const createId = () => Math.random().toString(36).slice(2, 9);

const cloneConfig = (config) => JSON.parse(JSON.stringify(config ?? {}));

const createMetricRow = (name = '', maximise = true) => ({
  id: createId(),
  name,
  maximise: !!maximise
});

const convertMetricsToRows = (metrics = {}) => {
  const entries = Object.entries(metrics ?? {});
  if (!entries.length) {
    return [createMetricRow('score', true)];
  }
  return entries.map(([metricName, maximise]) => createMetricRow(metricName, !!maximise));
};

const stringifyOptionValue = (value) => {
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  try {
    return JSON.stringify(value);
  } catch (error) {
    console.error('Unable to serialise option value', error);
    return '';
  }
};

const createOptionRow = (key = '', value = '') => ({
  id: createId(),
  key,
  value
});

const createModelRow = (modelName = '', options = []) => ({
  id: createId(),
  model: modelName,
  options: options.length ? options : [createOptionRow()]
});

const convertModelsToRows = (models = []) => {
  if (!Array.isArray(models) || !models.length) {
    return [createModelRow()];
  }
  return models.map((entry = {}) => {
    const { model, ...rest } = entry;
    const optionRows = Object.entries(rest).map(([key, value]) => createOptionRow(key, stringifyOptionValue(value)));
    return createModelRow(model ?? '', optionRows);
  });
};

const coerceOptionValue = (value) => {
  const trimmed = (value ?? '').trim();
  if (trimmed === '') return '';
  if (trimmed === 'true' || trimmed === 'false') {
    return trimmed === 'true';
  }
  const numeric = Number(trimmed);
  if (!Number.isNaN(numeric)) {
    return numeric;
  }
  return value;
};

const parseNumber = (value, fallback) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
};

export default function Problems() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [problems, setProblems] = useState({ list: [], count: 0 });
  const [statuses, setStatuses] = useState({});
  const [modalState, setModalState] = useState({ open: false, problem: null });
  const [formError, setFormError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [runningAction, setRunningAction] = useState(null);
  const [deletingId, setDeletingId] = useState(null);
  const [modelsCatalog, setModelsCatalog] = useState({ list: [], count: 0 });
  const [modelsLoading, setModelsLoading] = useState(false);
  const [modelsError, setModelsError] = useState(null);
  const [generalConfig, setGeneralConfig] = useState({
    iterations: DEFAULT_CONFIG.iterations,
    explorationConstant: DEFAULT_CONFIG.explorationConstant,
    promptPath: DEFAULT_CONFIG.promptPath ?? ''
  });
  const [solutionConfig, setSolutionConfig] = useState({
    ...DEFAULT_CONFIG.solution
  });
  const [metricsRows, setMetricsRows] = useState(() => convertMetricsToRows(DEFAULT_CONFIG.metrics));
  const [llmRows, setLlmRows] = useState(() => convertModelsToRows(DEFAULT_CONFIG.llm?.models ?? []));
  const [llmExtra, setLlmExtra] = useState({});
  const [extraConfig, setExtraConfig] = useState({});
  const [activeTab, setActiveTab] = useState(TABS[0].key);

  const navigate = useNavigate();

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors }
  } = useForm({
    resolver: yupResolver(schema),
    defaultValues: {
      name: ''
    }
  });

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const [problemPayload, statusPayload] = await Promise.all([
        fetchProblems({ query: { limit: 100, sort: 'date_created', order: 'desc' } }),
        fetchProblemStatuses()
      ]);
      setProblems(problemPayload ?? { list: [], count: 0 });
      setStatuses(statusPayload ?? {});
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    let isActive = true;
    const fetchCatalog = async () => {
      try {
        setModelsLoading(true);
        setModelsError(null);
        const payload = await fetchModels({ query: { limit: 100, sort: 'name', order: 'asc' } });
        if (!isActive) return;
        setModelsCatalog(payload ?? { list: [], count: 0 });
      } catch (err) {
        if (!isActive) return;
        setModelsError(err);
      } finally {
        if (isActive) {
          setModelsLoading(false);
        }
      }
    };
    fetchCatalog();
    return () => {
      isActive = false;
    };
  }, []);

  const initializeForm = useCallback(
    (problem) => {
      const baseConfig = cloneConfig(problem?.config ?? DEFAULT_CONFIG);

      const {
        metrics: configMetrics = DEFAULT_CONFIG.metrics,
        llm: configLlm = DEFAULT_CONFIG.llm,
        solution: configSolution = DEFAULT_CONFIG.solution,
        promptPath,
        iterations,
        explorationConstant,
        ...rest
      } = baseConfig;

      const llmConfig = configLlm ?? {};
      const { models: llmModels = [], ...llmRest } = llmConfig;

      reset({
        name: problem?.name ?? ''
      });

      setGeneralConfig({
        iterations: iterations ?? DEFAULT_CONFIG.iterations,
        explorationConstant: explorationConstant ?? DEFAULT_CONFIG.explorationConstant,
        promptPath: promptPath ?? ''
      });
      setSolutionConfig({
        ...DEFAULT_CONFIG.solution,
        ...(configSolution ?? {})
      });
      setMetricsRows(convertMetricsToRows(configMetrics ?? {}));
      setLlmRows(convertModelsToRows(llmModels));
      setLlmExtra(llmRest);
      setExtraConfig(rest);
      setActiveTab(TABS[0].key);
      setFormError(null);
    },
    [reset]
  );

  const actions = useMemo(
    () => (
      <div style={{ display: 'flex', gap: 12 }}>
        <button className="button secondary" onClick={load}>
          <RefreshCcw size={16} /> Refresh
        </button>
        <button
          className="button"
          onClick={() => {
            initializeForm(null);
            setModalState({ open: true, problem: null });
          }}
        >
          <Plus size={16} /> New problem
        </button>
      </div>
    ),
    [initializeForm, load]
  );

  const meta = useMemo(
    () => ({
      title: 'Problems',
      subtitle: 'Launch, monitor and iterate on optimisation targets',
      actions
    }),
    [actions]
  );
  usePageMeta(meta);

  const closeModal = () => {
    setModalState({ open: false, problem: null });
    setFormError(null);
    reset({ name: '' });
    setActiveTab(TABS[0].key);
  };

  const buildConfig = useCallback(() => {
    const metrics = metricsRows.reduce((acc, { name, maximise }) => {
      const trimmedName = (name ?? '').trim();
      if (trimmedName) {
        acc[trimmedName] = !!maximise;
      }
      return acc;
    }, {});

    const models = llmRows
      .map(({ model, options }) => {
        const selectedModel = (model ?? '').trim();
        if (!selectedModel) {
          return null;
        }
        const optionMap = {};
        options.forEach(({ key, value }) => {
          const optionKey = (key ?? '').trim();
          if (!optionKey) {
            return;
          }
          optionMap[optionKey] = coerceOptionValue(value);
        });
        return {
          ...optionMap,
          model: selectedModel
        };
      })
      .filter(Boolean);

    const llm = {
      ...llmExtra,
      models
    };

    const promptPath = generalConfig.promptPath?.trim()
      ? generalConfig.promptPath.trim()
      : null;

    const solution = {
      ...solutionConfig,
      workspace: solutionConfig.workspace ?? '',
      path: solutionConfig.path ?? '',
      runner: solutionConfig.runner ?? '',
      evalTimeout: solutionConfig.evalTimeout ?? '',
      pattern: solutionConfig.pattern ?? '',
      fullRewrite: !!solutionConfig.fullRewrite
    };

    return {
      ...extraConfig,
      iterations: parseNumber(generalConfig.iterations, DEFAULT_CONFIG.iterations),
      explorationConstant: parseNumber(
        generalConfig.explorationConstant,
        DEFAULT_CONFIG.explorationConstant
      ),
      promptPath,
      solution,
      metrics: Object.keys(metrics).length ? metrics : { ...DEFAULT_CONFIG.metrics },
      llm
    };
  }, [extraConfig, generalConfig, llmExtra, llmRows, metricsRows, solutionConfig]);

  const previewConfig = useMemo(() => buildConfig(), [buildConfig]);
  const previewJson = useMemo(() => JSON.stringify(previewConfig, null, 2), [previewConfig]);

  const onSubmit = handleSubmit(async (values) => {
    try {
      setSubmitting(true);
      setFormError(null);
      const payload = {
        name: values.name.trim(),
        config: buildConfig()
      };
      if (modalState.problem) {
        await updateProblem(modalState.problem.id, payload);
      } else {
        await createProblem(payload);
      }
      closeModal();
      await load();
    } catch (err) {
      setFormError(err);
    } finally {
      setSubmitting(false);
    }
  });

  const triggerRunAction = async (problem, action) => {
    try {
      setRunningAction(problem.id + action);
      if (action === 'start') {
        await startProblem(problem.id);
      } else {
        await stopProblem(problem.id);
      }
      await load();
    } catch (err) {
      setError(err);
    } finally {
      setRunningAction(null);
    }
  };

  const handleDelete = async (problem) => {
    if (!window.confirm(`Delete problem "${problem.name}"? This cannot be undone.`)) {
      return;
    }
    try {
      setDeletingId(problem.id);
      await deleteProblem(problem.id);
      await load();
    } catch (err) {
      setError(err);
    } finally {
      setDeletingId(null);
    }
  };

  const startEdit = (problem) => {
    initializeForm(problem);
    setModalState({ open: true, problem });
  };

  const addMetricRow = () => {
    setMetricsRows((prev) => [...prev, createMetricRow('', true)]);
  };

  const updateMetricRow = (id, updates) => {
    setMetricsRows((prev) => prev.map((row) => (row.id === id ? { ...row, ...updates } : row)));
  };

  const removeMetricRow = (id) => {
    setMetricsRows((prev) => {
      const filtered = prev.filter((row) => row.id !== id);
      return filtered.length ? filtered : [createMetricRow('', true)];
    });
  };

  const addModelRow = () => {
    setLlmRows((prev) => [...prev, createModelRow()]);
  };

  const updateModelRow = (id, updates) => {
    setLlmRows((prev) => prev.map((row) => (row.id === id ? { ...row, ...updates } : row)));
  };

  const removeModelRow = (id) => {
    setLlmRows((prev) => {
      const filtered = prev.filter((row) => row.id !== id);
      return filtered.length ? filtered : [createModelRow()];
    });
  };

  const addOptionRow = (modelId) => {
    setLlmRows((prev) =>
      prev.map((row) =>
        row.id === modelId
          ? {
              ...row,
              options: [...row.options, createOptionRow()]
            }
          : row
      )
    );
  };

  const updateOptionRow = (modelId, optionId, updates) => {
    setLlmRows((prev) =>
      prev.map((row) =>
        row.id === modelId
          ? {
              ...row,
              options: row.options.map((option) =>
                option.id === optionId ? { ...option, ...updates } : option
              )
            }
          : row
      )
    );
  };

  const removeOptionRow = (modelId, optionId) => {
    setLlmRows((prev) =>
      prev.map((row) => {
        if (row.id !== modelId) {
          return row;
        }
        const filtered = row.options.filter((option) => option.id !== optionId);
        return {
          ...row,
          options: filtered.length ? filtered : [createOptionRow()]
        };
      })
    );
  };

  if (loading) {
    return <LoadingState message="Fetching configured problems..." />;
  }

  if (error) {
    return <ErrorState error={error} retry={load} />;
  }

  return (
    <div className="card">
      <div className="card-header" style={{ marginBottom: 20 }}>
        <div>
          <h2 className="card-title">Evolution problems</h2>
          <p className="card-subtitle">Each problem bundles prompts, evaluation and search parameters.</p>
        </div>
      </div>

      {problems.list?.length ? (
        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Iterations</th>
              <th>Status</th>
              <th style={{ width: 240 }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {problems.list.map((problem) => {
              const status = statuses?.[problem.id] ?? 'NOT_RUNNING';
              const isRunning = status === 'RUNNING';
              return (
                <tr key={problem.id}>
                  <td style={{ fontWeight: 600 }}>{problem.name}</td>
                  <td>{problem.config?.iterations ?? '—'}</td>
                  <td>
                    <StatusBadge status={status} />
                  </td>
                  <td>
                    <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
                      <button
                        className="button secondary"
                        onClick={() => navigate(`/problems/${problem.id}`)}
                      >
                        <FileJson size={16} /> Inspect
                      </button>
                      <button
                        className="button secondary"
                        onClick={() => startEdit(problem)}
                      >
                        <PenSquare size={16} /> Edit
                      </button>
                      <button
                        className="button secondary"
                        onClick={() => triggerRunAction(problem, isRunning ? 'stop' : 'start')}
                        disabled={runningAction === problem.id + (isRunning ? 'stop' : 'start')}
                      >
                        {isRunning ? <StopCircle size={16} /> : <Play size={16} />}
                        {runningAction === problem.id + (isRunning ? 'stop' : 'start')
                          ? isRunning
                            ? 'Stopping...'
                            : 'Starting...'
                          : isRunning
                          ? 'Stop'
                          : 'Start'}
                      </button>
                      <button
                        className="button secondary"
                        onClick={() => handleDelete(problem)}
                        disabled={deletingId === problem.id}
                      >
                        <Trash size={16} />
                        {deletingId === problem.id ? 'Removing...' : 'Remove'}
                      </button>
                    </div>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      ) : (
        <EmptyState
          title="No problems yet"
          description={'Use the "New problem" action to define your first optimisation target.'}
        />
      )}

      <Modal
        open={modalState.open}
        title={modalState.problem ? 'Edit problem' : 'Create problem'}
        description="Provide a name and configuration for the evolution run."
        onClose={closeModal}
        actions={
          <>
            <button className="button secondary" onClick={closeModal} disabled={submitting}>
              Cancel
            </button>
            <button className="button" onClick={onSubmit} disabled={submitting}>
              {submitting ? 'Saving...' : modalState.problem ? 'Save changes' : 'Create problem'}
            </button>
          </>
        }
      >
        <form className="form-grid" onSubmit={(event) => event.preventDefault()}>
          <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
            {TABS.map((tab) => (
              <button
                key={tab.key}
                type="button"
                onClick={() => setActiveTab(tab.key)}
                className={`button secondary${activeTab === tab.key ? ' active' : ''}`}
                style={{
                  background: activeTab === tab.key ? 'var(--color-surface-alt)' : undefined,
                  borderColor: activeTab === tab.key ? 'var(--color-border-strong)' : undefined
                }}
              >
                {tab.label}
              </button>
            ))}
          </div>
          {/* Scroll only the tab panels */}
          <div style={{ maxHeight: '55vh', overflowY: 'auto', overflowX: 'hidden', paddingRight: 8 }}>
            {activeTab === 'general' && (
              <div className="form-grid two-columns">
              <div className="field" style={{ gridColumn: '1 / span 2' }}>
                <label htmlFor="problem-name">Name</label>
                <input id="problem-name" placeholder="New evolution run" {...register('name')} />
                {errors.name && (
                  <span style={{ color: 'var(--color-danger)', fontSize: '0.8rem' }}>{errors.name.message}</span>
                )}
              </div>

              <div className="field">
                <label htmlFor="iterations">Iterations</label>
                <input
                  id="iterations"
                  type="number"
                  min={1}
                  value={generalConfig.iterations}
                  onChange={(event) =>
                    setGeneralConfig((prev) => ({ ...prev, iterations: event.target.value }))
                  }
                />
              </div>

              <div className="field">
                <label htmlFor="explorationConstant">Exploration constant</label>
                <input
                  id="explorationConstant"
                  type="number"
                  step="0.01"
                  value={generalConfig.explorationConstant}
                  onChange={(event) =>
                    setGeneralConfig((prev) => ({ ...prev, explorationConstant: event.target.value }))
                  }
                />
              </div>

              <div className="field" style={{ gridColumn: '1 / span 2' }}>
                <label htmlFor="promptPath">Prompt path (optional)</label>
                <input
                  id="promptPath"
                  placeholder="/path/to/prompts.yaml"
                  value={generalConfig.promptPath}
                  onChange={(event) =>
                    setGeneralConfig((prev) => ({ ...prev, promptPath: event.target.value }))
                  }
                />
              </div>

              <div className="field">
                <label htmlFor="solution-workspace">Solution workspace</label>
                <input
                  id="solution-workspace"
                  placeholder="/tmp"
                  value={solutionConfig.workspace ?? ''}
                  onChange={(event) =>
                    setSolutionConfig((prev) => ({ ...prev, workspace: event.target.value }))
                  }
                />
              </div>

              <div className="field">
                <label htmlFor="solution-path">Solution path</label>
                <input
                  id="solution-path"
                  placeholder="solution"
                  value={solutionConfig.path ?? ''}
                  onChange={(event) =>
                    setSolutionConfig((prev) => ({ ...prev, path: event.target.value }))
                  }
                />
              </div>

              <div className="field">
                <label htmlFor="solution-runner">Solution runner</label>
                <input
                  id="solution-runner"
                  placeholder="runner"
                  value={solutionConfig.runner ?? ''}
                  onChange={(event) =>
                    setSolutionConfig((prev) => ({ ...prev, runner: event.target.value }))
                  }
                />
              </div>

              <div className="field">
                <label htmlFor="solution-timeout">Evaluation timeout</label>
                <input
                  id="solution-timeout"
                  placeholder="PT1M"
                  value={solutionConfig.evalTimeout ?? ''}
                  onChange={(event) =>
                    setSolutionConfig((prev) => ({ ...prev, evalTimeout: event.target.value }))
                  }
                />
              </div>

              <div className="field">
                <label htmlFor="solution-pattern">File pattern</label>
                <input
                  id="solution-pattern"
                  placeholder=".*\\.py$"
                  value={solutionConfig.pattern ?? ''}
                  onChange={(event) =>
                    setSolutionConfig((prev) => ({ ...prev, pattern: event.target.value }))
                  }
                />
              </div>

              <div className="field" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <input
                  id="solution-full-rewrite"
                  type="checkbox"
                  checked={!!solutionConfig.fullRewrite}
                  onChange={(event) =>
                    setSolutionConfig((prev) => ({ ...prev, fullRewrite: event.target.checked }))
                  }
                />
                <label htmlFor="solution-full-rewrite" style={{ margin: 0 }}>
                  Full rewrite
                </label>
              </div>
            </div>
          )}

          {activeTab === 'metrics' && (
            <div className="form-grid" style={{ gap: 16 }}>
              {metricsRows.map((metric) => (
                <div
                  key={metric.id}
                  className="card"
                  style={{ padding: 16, display: 'grid', gap: 12, gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', minWidth: 0 }}
                >
                  <div className="field">
                    <label htmlFor={`metric-${metric.id}`}>Metric name</label>
                    <input
                      id={`metric-${metric.id}`}
                      placeholder="score"
                      value={metric.name}
                      onChange={(event) =>
                        updateMetricRow(metric.id, { name: event.target.value })
                      }
                    />
                  </div>
                  <div className="field">
                    <label htmlFor={`metric-mode-${metric.id}`}>Objective</label>
                    <select
                      id={`metric-mode-${metric.id}`}
                      value={metric.maximise ? 'maximise' : 'minimise'}
                      onChange={(event) =>
                        updateMetricRow(metric.id, { maximise: event.target.value === 'maximise' })
                      }
                    >
                      <option value="maximise">Maximise</option>
                      <option value="minimise">Minimise</option>
                    </select>
                  </div>
                  <div className="field" style={{ alignSelf: 'flex-end' }}>
                    <button
                      type="button"
                      className="button secondary"
                      onClick={() => removeMetricRow(metric.id)}
                    >
                      <Trash size={16} /> Remove metric
                    </button>
                  </div>
                </div>
              ))}
              <button type="button" className="button" onClick={addMetricRow}>
                <Plus size={16} /> Add metric
              </button>
            </div>
          )}

          {activeTab === 'llm' && (
            <div className="form-grid" style={{ gap: 16 }}>
              {modelsError && (
                <div className="field" style={{ color: 'var(--color-danger)' }}>
                  {modelsError.message}
                </div>
              )}
              {llmRows.map((row) => {
                const catalogValues = new Set(
                  (modelsCatalog.list ?? []).map((model) => model.name ?? model.id)
                );

                const hasCustomValue = row.model && !catalogValues.has(row.model);

                return (
                  <div key={row.id} className="card" style={{ padding: 16, display: 'grid', gap: 16, minWidth: 0 }}>
                    <div className="field">
                      <label htmlFor={`llm-model-${row.id}`}>Model</label>
                      <select
                        id={`llm-model-${row.id}`}
                        value={row.model}
                        onChange={(event) =>
                          updateModelRow(row.id, { model: event.target.value })
                        }
                      >
                        <option value="">Select a model</option>
                        {(modelsCatalog.list ?? []).map((model) => {
                          const optionValue = model.name ?? model.id;
                          return (
                            <option key={model.id ?? optionValue} value={optionValue}>
                              {model.name || model.id}
                              {model.id && model.name && model.id !== model.name
                                ? ` (${model.id})`
                                : ''}
                            </option>
                          );
                        })}
                        {hasCustomValue && <option value={row.model}>{row.model}</option>}
                      </select>
                      {modelsLoading && (
                        <small style={{ color: 'var(--color-text-muted)' }}>Loading models…</small>
                      )}
                    </div>

                    <div className="field" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                      <label style={{ fontWeight: 600 }}>Options</label>
                      {row.options.map((option) => (
                        <div
                          key={option.id}
                          style={{ display: 'grid', gap: 12, gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', alignItems: 'center' }}
                        >
                          <input
                            placeholder="temperature"
                            value={option.key}
                            onChange={(event) =>
                              updateOptionRow(row.id, option.id, { key: event.target.value })
                            }
                          />
                          <input
                            placeholder="0.7"
                            value={option.value}
                            onChange={(event) =>
                              updateOptionRow(row.id, option.id, { value: event.target.value })
                            }
                          />
                          <button
                            type="button"
                            className="button secondary"
                            onClick={() => removeOptionRow(row.id, option.id)}
                          >
                            <Trash size={16} />
                          </button>
                        </div>
                      ))}
                      <button
                        type="button"
                        className="button secondary"
                        onClick={() => addOptionRow(row.id)}
                      >
                        <Plus size={16} /> Add option
                      </button>
                    </div>

                    <div className="field" style={{ alignSelf: 'flex-end' }}>
                      <button
                        type="button"
                        className="button secondary"
                        onClick={() => removeModelRow(row.id)}
                      >
                        <Trash size={16} /> Remove model
                      </button>
                    </div>
                  </div>
                );
              })}
              <button type="button" className="button" onClick={addModelRow}>
                <Plus size={16} /> Add LLM model
              </button>
            </div>
          )}

          {formError && (
            <div className="field" style={{ color: 'var(--color-danger)' }}>{formError.message}</div>
          )}
          </div>
        </form>
      </Modal>
    </div>
  );
}
