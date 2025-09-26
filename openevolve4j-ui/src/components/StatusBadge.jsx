import clsx from 'clsx';

const STATUS_STYLES = {
  RUNNING: 'success',
  ACTIVE: 'success',
  READY: 'success',
  FAILED: 'danger',
  ERROR: 'danger',
  STOPPED: '',
  NOT_RUNNING: ''
};

export function StatusBadge({ status }) {
  const normalized = (status ?? '').toString().toUpperCase();
  const variant = STATUS_STYLES[normalized] ?? '';

  return (
    <span className={clsx('badge', variant && variant)}>{normalized || 'UNKNOWN'}</span>
  );
}
