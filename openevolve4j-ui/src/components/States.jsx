import { AlertCircle, Loader2 } from 'lucide-react';

export function LoadingState({ message = 'Loading data...' }) {
  return (
    <div className="empty-state" style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
      <Loader2 className="animate-spin" size={20} />
      <span>{message}</span>
    </div>
  );
}

export function ErrorState({ error, retry }) {
  return (
    <div className="empty-state" style={{ borderColor: 'rgba(248, 113, 113, 0.35)' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 12 }}>
        <AlertCircle color="var(--color-danger)" size={20} />
        <div>
          <strong>Something went wrong</strong>
          <p style={{ margin: '6px 0 0', color: 'var(--color-text-muted)' }}>{error?.message ?? String(error)}</p>
        </div>
      </div>
      {retry && (
        <button className="button" style={{ marginTop: 16 }} onClick={retry}>
          Try again
        </button>
      )}
    </div>
  );
}

export function EmptyState({ title = 'Nothing here yet', description }) {
  return (
    <div className="empty-state">
      <p style={{ margin: 0, fontWeight: 600 }}>{title}</p>
      {description && (
        <p style={{ margin: '8px 0 0', color: 'var(--color-text-muted)' }}>{description}</p>
      )}
    </div>
  );
}
