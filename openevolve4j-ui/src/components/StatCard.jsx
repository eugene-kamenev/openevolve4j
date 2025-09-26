export default function StatCard({ icon: Icon, label, value, description, accent }) {
  return (
    <div
      className="card"
      style={{ background: 'linear-gradient(160deg, rgba(17, 24, 39, 0.95), rgba(15, 23, 42, 0.85))' }}
    >
      <div className="card-header" style={{ marginBottom: 8 }}>
        <div className="badge" style={{ background: accent ?? 'rgba(56,189,248,0.18)' }}>
          {Icon && <Icon size={16} strokeWidth={1.8} />}
          <span>{label}</span>
        </div>
      </div>
      <div style={{ fontSize: '2rem', fontWeight: 600 }}>{value}</div>
      {description && (
        <p className="card-subtitle" style={{ marginTop: 12 }}>
          {description}
        </p>
      )}
    </div>
  );
}
