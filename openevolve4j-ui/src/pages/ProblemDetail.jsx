import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { format } from 'date-fns';
import { ChevronLeft, Play, RefreshCcw, StopCircle } from 'lucide-react';
import { LoadingState, ErrorState, EmptyState } from '../components/States.jsx';
import SolutionDetailsPanel from '../components/SolutionDetailsPanel.jsx';
import { usePageMeta } from '../hooks/usePageMeta.js';
import {
  fetchProblem,
  fetchProblemRuns,
  fetchProblemSolutions,
  fetchProblemStatuses,
  startProblem,
  stopProblem
} from '../services/problems.js';


function formatFitness(metrics, fitness = {}) {
  if (fitness?.error) {
    return `Error: ${fitness.error}`;
  }
  return Object.keys(metrics)
    .map((m) => `${m}: ${typeof fitness[m] === 'number' ? fitness[m].toFixed(3) : fitness[m]}`).join(', ');
}

function truncateId(id) {
  if (!id) return '—';
  return id.length > 8 ? `${id.substring(0, 8)}` : id;
}

const SOLUTIONS_PAGE_SIZE = 20;

export default function ProblemDetail() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [problem, setProblem] = useState(null);
  const [solutions, setSolutions] = useState({ list: [], count: 0 });
  const [runs, setRuns] = useState({ list: [], count: 0 });
  const [solutionsPage, setSolutionsPage] = useState(0);
  const [selectedRun, setSelectedRun] = useState('');
  const [selectedModel, setSelectedModel] = useState('');
  const [sortBy, setSortBy] = useState('date_created');
  const [solutionsLoading, setSolutionsLoading] = useState(false);
  const [status, setStatus] = useState('NOT_RUNNING');
  const [pendingAction, setPendingAction] = useState(false);
  const [selectedSolution, setSelectedSolution] = useState(null);

  const load = useCallback(
    async (displaySpinner = true) => {
      try {
        if (displaySpinner) setLoading(true);
        setError(null);
        const [problemData, runsData, statusesData] = await Promise.all([
          fetchProblem(id),
          fetchProblemRuns(id, { query: { limit: 50, sort: 'date_created', order: 'desc' } }),
          fetchProblemStatuses()
        ]);
        setProblem(problemData);
        setRuns(runsData);
        setStatus(statusesData?.[id] ?? 'NOT_RUNNING');
      } catch (err) {
        setError(err);
      } finally {
        setLoading(false);
      }
    },
    [id]
  );

  useEffect(() => {
    setSolutionsPage(0);
    setSelectedRun('');
    setSelectedSolution(null);
    setSelectedModel('');
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  // Reload solutions when selected run changes. Use `forRun` filter when a run is selected.
  useEffect(() => {
    if (!problem) return;

    let cancelled = false;

    async function reloadSolutions() {
      try {
        setError(null);
        setSolutionsLoading(true);
        setSelectedSolution(null);

        const query = {
          limit: SOLUTIONS_PAGE_SIZE,
          offset: solutionsPage * SOLUTIONS_PAGE_SIZE,
          order: 'desc'
        };
        const filters = {};

        if (sortBy === 'date_created') {
          query.sort = 'date_created';
        } else if (sortBy === 'fitness') {
          filters.sort = problem.config.metrics;
        }

        if (selectedRun) {
          filters.forRun = selectedRun;
        }

        if (selectedModel) {
          filters.forModel = selectedModel;
        }

        const options = Object.keys(filters).length ? { query, filters } : { query };
        const solutionsData = await fetchProblemSolutions(id, options);

        if (cancelled) return;

        const totalItems = solutionsData?.count ?? 0;
        const maxPageIndex = totalItems > 0 ? Math.ceil(totalItems / SOLUTIONS_PAGE_SIZE) - 1 : 0;

        if (solutionsPage > 0 && solutionsPage > maxPageIndex) {
          setSolutionsPage(Math.max(0, maxPageIndex));
          return;
        }

        setSolutions(solutionsData);
      } catch (err) {
        if (!cancelled) setError(err);
      } finally {
        if (!cancelled) setSolutionsLoading(false);
      }
    }

    reloadSolutions();

    return () => {
      cancelled = true;
    };
  }, [id, problem, selectedRun, selectedModel, sortBy, solutionsPage]);

  const triggerRun = useCallback(
    async (action) => {
      try {
        setPendingAction(true);
        if (action === 'start') {
          await startProblem(id);
        } else {
          await stopProblem(id);
        }
        await load(false);
      } catch (err) {
        setError(err);
      } finally {
        setPendingAction(false);
      }
    },
    [id, load]
  );

  const actions = useMemo(
    () => (
      <div style={{ display: 'flex', gap: 12 }}>
        <button className="button secondary" onClick={() => navigate(-1)}>
          <ChevronLeft size={16} /> Back
        </button>
        <button className="button secondary" onClick={() => load(false)}>
          <RefreshCcw size={16} /> Refresh
        </button>
        <button
          className="button"
          onClick={() => triggerRun(status === 'RUNNING' ? 'stop' : 'start')}
          disabled={pendingAction}
        >
          {pendingAction ? (
            status === 'RUNNING' ? 'Stopping...' : 'Starting...'
          ) : status === 'RUNNING' ? (
            <>
              <StopCircle size={16} /> Stop run
            </>
          ) : (
            <>
              <Play size={16} /> Start run
            </>
          )}
        </button>
      </div>
    ),
    [navigate, load, triggerRun, status, pendingAction]
  );

  const meta = useMemo(
    () => ({
      title: problem ? problem.name : 'Problem detail',
      subtitle: 'Drill into the configuration and live signals of your evolution run',
      actions
    }),
    [problem, actions]
  );
  usePageMeta(meta);

  if (loading) {
    return <LoadingState message="Loading problem details..." />;
  }

  if (error) {
    return <ErrorState error={error} retry={() => load(true)} />;
  }

  if (!problem) {
    return <EmptyState title="Problem not found" description="The requested problem does not exist." />;
  }

  const totalSolutions = solutions.count ?? 0;
  const currentPageSize = solutions.list?.length ?? 0;
  const pageStart = currentPageSize ? solutionsPage * SOLUTIONS_PAGE_SIZE + 1 : 0;
  const pageEnd = currentPageSize ? pageStart + currentPageSize - 1 : 0;
  const totalPages = totalSolutions > 0 ? Math.ceil(totalSolutions / SOLUTIONS_PAGE_SIZE) : 0;
  const canGoPrev = solutionsPage > 0;
  const canGoNext = (solutionsPage + 1) * SOLUTIONS_PAGE_SIZE < totalSolutions;

  return (
    <div className="grid" style={{ gap: 28 }}>
      <section className="card">
        <div className="card-header">
          <div>
            <h2 className="card-title">Evolution run</h2>
            <p className="card-subtitle">Filter solutions by evolution run</p>
          </div>
        </div>
        <div style={{ padding: 12 }}>
          <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start', flexWrap: 'wrap' }}>
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <label style={{ display: 'block', marginBottom: 8 }}>Select run</label>
              <select
                value={selectedRun}
                onChange={(e) => {
                  setSelectedRun(e.target.value);
                  setSolutionsPage(0);
                }}
                style={{ minWidth: 300 }}
              >
                <option value="">All runs</option>
                {runs.list.map((r) => (
                  <option key={r.id} value={r.payload?.id}>
                    {truncateId(r.payload?.id)} {r.dateCreated ? `— ${format(new Date(r.dateCreated), 'PPpp')}` : ''}
                  </option>
                ))}
              </select>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <label style={{ display: 'block', marginBottom: 8 }}>Select model</label>
              <select
                value={selectedModel}
                onChange={(e) => {
                  setSelectedModel(e.target.value);
                  setSolutionsPage(0);
                }}
                style={{ minWidth: 300 }}
              >
                <option value="">All models</option>
                {problem?.config?.llm?.models?.map((m) => (
                  <option key={m.model} value={m.model}>{m.model}</option>
                ))}
              </select>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <label style={{ display: 'block', marginBottom: 8 }}>Sort by</label>
              <select
                value={sortBy}
                onChange={(e) => {
                  setSortBy(e.target.value);
                  setSolutionsPage(0);
                }}
                style={{ minWidth: 300 }}
              >
                <option value="date_created">Date Created</option>
                <option value="fitness">Fitness</option>
              </select>
            </div>
          </div>
        </div>
      </section>

      <section className="card" style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
        <div className="card-header">
          <div>
            <h2 className="card-title">Solutions {totalSolutions}</h2>
            <p className="card-subtitle">Latest candidate solutions emitted by the search</p>
          </div>
        </div>
        {solutionsLoading ? (
          <div style={{ padding: 18 }}>
            <LoadingState message="Loading solutions..." />
          </div>
        ) : solutions.list?.length ? (
          <div className="solutions-container" style={{ padding: 20, flex: 1 }}>
            <div className="solutions-list" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <table key={`${selectedRun || 'all'}-${sortBy}-${solutionsPage}`} className="table">
                <thead>
                  <tr>
                    <th>Solution ID</th>
                    <th>Parent</th>
                    <th>Model</th>
                    <th>Date Created</th>
                    <th>Fitness</th>
                  </tr>
                </thead>
                <tbody>
                  {solutions.list.map((event) => (
                    <tr
                      key={event.id}
                      className={selectedSolution?.id === event.id ? 'selected' : ''}
                      onClick={() => setSelectedSolution(event)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                          e.preventDefault();
                          setSelectedSolution(event);
                        }
                      }}
                      role="button"
                      tabIndex={0}
                    >
                      <td title={event.payload?.id}>{truncateId(event.payload?.id)}</td>
                      <td title={event.payload?.parentId}>{truncateId(event.payload?.parentId)}</td>
                      <td title={event.payload?.data?.metadata?.llmModel}>{event.payload?.data?.metadata?.llmModel}</td>
                      <td title={event.dateCreated}>{new Date(event.dateCreated).toLocaleString()}</td>
                      <td>{formatFitness(problem.config.metrics, event.payload?.fitness)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <div
                className="pagination-controls"
                style={{
                  marginTop: 8,
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  gap: 12,
                  flexWrap: 'wrap'
                }}
              >
                <span style={{ fontSize: 12, color: 'var(--subtle-text, #6b7280)' }}>
                  {totalSolutions
                    ? `Showing ${pageStart}-${pageEnd} of ${totalSolutions}`
                    : 'Showing 0 of 0'}
                </span>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <button
                    className="button secondary"
                    onClick={() => setSolutionsPage((prev) => Math.max(prev - 1, 0))}
                    disabled={!canGoPrev}
                  >
                    Previous
                  </button>
                  <span style={{ fontSize: 12, minWidth: 90, textAlign: 'center' }}>
                    {totalPages ? `Page ${solutionsPage + 1} of ${totalPages}` : 'Page 0 of 0'}
                  </span>
                  <button
                    className="button secondary"
                    onClick={() => setSolutionsPage((prev) => prev + 1)}
                    disabled={!canGoNext}
                  >
                    Next
                  </button>
                </div>
              </div>
            </div>
            <SolutionDetailsPanel
              problem={problem}
              solution={selectedSolution}
              onClose={() => setSelectedSolution(null)}
            />
          </div>
        ) : (
          <EmptyState title="No solutions yet" description="Solutions will appear as the run explores the search space." />
        )}
      </section>
    </div>
  );
}
