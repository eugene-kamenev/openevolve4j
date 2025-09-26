import { X } from 'lucide-react';

export default function Modal({ open, title, description, onClose, actions, children, contentStyle, bodyStyle }) {
  if (!open) return null;
  // Inline styles applied to guard against wide children overflowing the viewport
  const overlayStyle = {
    // keep existing stacking/positioning from CSS but prevent horizontal overflow
    overflowX: 'hidden'
  };

  const modalStyle = {
    // limit modal width to viewport with some padding and allow internal vertical scroll
    maxWidth: 'calc(100vw - 40px)',
    width: 'auto',
    boxSizing: 'border-box',
    overflow: 'hidden',
    display: 'flex',
    flexDirection: 'column',
    maxHeight: '90vh',
    ...(contentStyle || {})
  };

  const computedBodyStyle = {
    // allow modal content to scroll vertically but prevent horizontal scrolling
    overflowY: 'auto',
    overflowX: 'hidden',
    paddingTop: 8,
    display: 'block',
    ...(bodyStyle || {})
  };

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" style={overlayStyle}>
      <div className="modal" style={modalStyle}>
        <div className="modal-header">
          <div>
            <h2 className="card-title" style={{ margin: 0 }}>{title}</h2>
            {description && (
              <p className="card-subtitle" style={{ marginTop: 6 }}>{description}</p>
            )}
          </div>
          <button className="button secondary" onClick={onClose} style={{ padding: 8, borderRadius: 10 }}>
            <X size={18} />
          </button>
        </div>
  <div className="modal-body" style={computedBodyStyle}>{children}</div>
        {actions && <div className="modal-footer">{actions}</div>}
      </div>
    </div>
  );
}
