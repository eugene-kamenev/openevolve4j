import { useCallback, useEffect, useMemo, useState } from 'react';
import { Plus, RefreshCcw, Trash } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import Modal from '../components/Modal.jsx';
import { LoadingState, ErrorState, EmptyState } from '../components/States.jsx';
import { usePageMeta } from '../hooks/usePageMeta.js';
import { createModel, deleteModel, fetchModels } from '../services/models.js';

const formSchema = yup.object({
  name: yup.string().required('Model name is required'),
  id: yup.string().optional()
});

export default function Models() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [models, setModels] = useState({ list: [], count: 0 });
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [formError, setFormError] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [deletingId, setDeletingId] = useState(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors }
  } = useForm({
    resolver: yupResolver(formSchema),
    defaultValues: {
      name: '',
      id: ''
    }
  });

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const payload = await fetchModels({ query: { limit: 50, sort: 'name', order: 'asc' } });
      setModels(payload ?? { list: [], count: 0 });
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const actions = useMemo(
    () => (
      <div style={{ display: 'flex', gap: 12 }}>
        <button className="button secondary" onClick={load}>
          <RefreshCcw size={16} /> Refresh
        </button>
        <button className="button" onClick={() => setIsModalOpen(true)}>
          <Plus size={16} /> Add model
        </button>
      </div>
    ),
    [load]
  );

  const meta = useMemo(
    () => ({
      title: 'Models',
      subtitle: 'Manage LiteLLM-backed endpoints available to evolutionary runs',
      actions
    }),
    [actions]
  );
  usePageMeta(meta);

  const onSubmit = handleSubmit(async (values) => {
    try {
      setSubmitting(true);
      setFormError(null);
      const payload = {
        name: values.name.trim(),
        ...(values.id ? { id: values.id.trim() } : {})
      };
      await createModel(payload);
      setIsModalOpen(false);
      reset({ name: '', id: '' });
      await load();
    } catch (err) {
      setFormError(err);
    } finally {
      setSubmitting(false);
    }
  });

  const handleDelete = async (id) => {
    if (!window.confirm('Remove this model from the registry?')) {
      return;
    }
    try {
      setDeletingId(id);
      await deleteModel(id);
      await load();
    } catch (err) {
      setError(err);
    } finally {
      setDeletingId(null);
    }
  };

  if (loading) {
    return <LoadingState message="Loading registered models..." />;
  }

  if (error) {
    return <ErrorState error={error} retry={load} />;
  }

  return (
    <div className="card">
      <div className="card-header" style={{ marginBottom: 20 }}>
        <div>
          <h2 className="card-title">Registered models</h2>
          <p className="card-subtitle">
            Every entry encapsulates an upstream LLM accessible via LiteLLM and your API key.
          </p>
        </div>
      </div>

      {models.list?.length ? (
        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Identifier</th>
              <th style={{ width: 120 }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {models.list.map((model) => (
              <tr key={model.id}>
                <td style={{ fontWeight: 600 }}>{model.name}</td>
                <td style={{ color: 'var(--color-text-muted)', fontSize: '0.85rem' }}>{model.id}</td>
                <td>
                  <button
                    className="button secondary"
                    onClick={() => handleDelete(model.id)}
                    disabled={deletingId === model.id}
                    style={{ width: '100%' }}
                  >
                    <Trash size={16} /> {deletingId === model.id ? 'Removing...' : 'Remove'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <EmptyState
          title="No models found"
          description={'Use the "Add model" action to register a model exposed by LiteLLM.'}
        />
      )}

      <Modal
        open={isModalOpen}
        title="Register a model"
        description="Define a friendly name and optionally provide a UUID if you have one."
        onClose={() => {
          setIsModalOpen(false);
          setFormError(null);
        }}
        actions={
          <>
            <button className="button secondary" onClick={() => setIsModalOpen(false)} disabled={submitting}>
              Cancel
            </button>
            <button className="button" onClick={onSubmit} disabled={submitting}>
              {submitting ? 'Saving...' : 'Save model'}
            </button>
          </>
        }
      >
        <form className="form-grid" onSubmit={(event) => event.preventDefault()}>
          <div className="field">
            <label htmlFor="name">Name</label>
            <input id="name" placeholder="gpt-4.1-mini" {...register('name')} />
            {errors.name && <span style={{ color: 'var(--color-danger)', fontSize: '0.8rem' }}>{errors.name.message}</span>}
          </div>

          <div className="field">
            <label htmlFor="id">Identifier (optional)</label>
            <input id="id" placeholder="Let the server generate one" {...register('id')} />
          </div>

          {formError && (
            <div className="field" style={{ color: 'var(--color-danger)' }}>
              {formError.message}
            </div>
          )}
        </form>
      </Modal>
    </div>
  );
}
