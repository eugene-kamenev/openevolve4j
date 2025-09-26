import { useEffect, useMemo, useState } from 'react';
import { ActivitySquare, Brain, Layers, ListTree, MoveRight } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import StatCard from '../components/StatCard.jsx';
import { LoadingState, ErrorState, EmptyState } from '../components/States.jsx';
import { usePageMeta } from '../hooks/usePageMeta.js';
import { fetchModels } from '../services/models.js';
import { fetchProblems, fetchProblemStatuses } from '../services/problems.js';
import { StatusBadge } from '../components/StatusBadge.jsx';

export default function Dashboard() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [models, setModels] = useState({ list: [], count: 0 });
  const [problems, setProblems] = useState({ list: [], count: 0 });
  const [statuses, setStatuses] = useState({});
  const navigate = useNavigate();

  const meta = useMemo(
    () => ({
      title: 'Dashboard',
      subtitle: 'Quick glance at your models, problems and active runs'
    }),
    []
  );
  usePageMeta(meta);

  useEffect(() => {
    let isMounted = true;
    async function load() {
      try {
        setLoading(true);
        setError(null);
        const [modelsPayload, problemsPayload, statusesPayload] = await Promise.all([
          fetchModels({ query: { limit: 5, sort: 'date_created', order: 'desc' } }),
          fetchProblems({ query: { limit: 5, sort: 'date_created', order: 'desc' } }),
          fetchProblemStatuses()
        ]);
        if (!isMounted) return;
        setModels(modelsPayload ?? { list: [], count: 0 });
        setProblems(problemsPayload ?? { list: [], count: 0 });
        setStatuses(statusesPayload ?? {});
      } catch (err) {
        if (isMounted) {
          setError(err);
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    }
    load();
    return () => {
      isMounted = false;
    };
  }, []);

  if (loading) {
    return <LoadingState message="Preparing dashboard metrics..." />;
  }

  if (error) {
    return <ErrorState error={error} retry={() => window.location.reload()} />;
  }

  const activeRuns = Object.values(statuses ?? {}).filter((value) => {
    if (!value) return false;
    const normalized = value.toString().toUpperCase();
    return normalized === 'RUNNING';
  }).length;

  return (
    <div className="grid" style={{ gap: 28 }}>
      <section className="grid col-2">
        <StatCard
          icon={Brain}
          label="Models"
          value={models.count ?? models.list?.length ?? 0}
          description="Tracked inference endpoints available to optimisation runs"
        />
        <StatCard
          icon={ListTree}
          label="Problems"
          value={problems.count ?? problems.list?.length ?? 0}
          description="Configured optimisation targets ready to execute"
          accent="rgba(129, 140, 248, 0.2)"
        />
        <StatCard
          icon={ActivitySquare}
          label="Active runs"
          value={activeRuns}
          description={
            activeRuns > 0
              ? 'Evolution in progress — monitor the stream for insights'
              : 'No active tasks right now. Spin up a run from the problems view.'
          }
          accent="rgba(52, 211, 153, 0.2)"
        />
        <StatCard
          icon={Layers}
          label="Latest updates"
          value={new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
          description="All data shown reflects the latest refresh"
          accent="rgba(248, 113, 113, 0.2)"
        />
      </section>

      <section className="card">
        <div className="card-header">
          <div>
            <h2 className="card-title">Recent problems</h2>
            <p className="card-subtitle">Monitor configuration and jump into detail</p>
          </div>
          <button className="button secondary" onClick={() => navigate('/problems')}>
            View all
            <MoveRight size={16} />
          </button>
        </div>

        {problems.list?.length ? (
          <table className="table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Iterations</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {problems.list.map((problem) => {
                const status = statuses?.[problem.id];
                return (
                  <tr key={problem.id}>
                    <td style={{ fontWeight: 600 }}>{problem.name}</td>
                    <td>{problem.config?.iterations ?? '—'}</td>
                    <td>
                      <StatusBadge status={status ?? 'NOT_RUNNING'} />
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <button className="button secondary" onClick={() => navigate(`/problems/${problem.id}`)}>
                        Inspect
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        ) : (
          <EmptyState
            title="No problems configured"
            description="Create your first optimisation problem to start experimenting."
          />
        )}
      </section>

      <section className="card">
        <div className="card-header">
          <div>
            <h2 className="card-title">Models</h2>
            <p className="card-subtitle">LLM endpoints available for runs</p>
          </div>
          <button className="button secondary" onClick={() => navigate('/models')}>
            Manage models
            <MoveRight size={16} />
          </button>
        </div>

        {models.list?.length ? (
          <table className="table">
            <thead>
              <tr>
                <th>Name</th>
                <th>ID</th>
              </tr>
            </thead>
            <tbody>
              {models.list.map((model) => (
                <tr key={model.id}>
                  <td style={{ fontWeight: 600 }}>{model.name}</td>
                  <td style={{ color: 'var(--color-text-muted)', fontSize: '0.85rem' }}>{model.id}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <EmptyState
            title="No models registered"
            description="Connected LiteLLM endpoints will appear here after syncing."
          />
        )}
      </section>
    </div>
  );
}
