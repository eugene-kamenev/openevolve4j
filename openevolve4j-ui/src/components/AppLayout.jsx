import { useCallback, useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { Brain, Layers3, ListTree, LayoutDashboard } from 'lucide-react';
import clsx from 'clsx';

const NAV_LINKS = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/models', label: 'Models', icon: Brain },
  { to: '/problems', label: 'Problems', icon: ListTree },
  { to: '/studio', label: 'Evolution Studio', icon: Layers3, disabled: true }
];

export default function AppLayout() {
  const [pageMeta, setPageMeta] = useState({
    title: 'Dashboard',
    subtitle: 'Overview of your evolution workspace',
    actions: null
  });

  const updatePageMeta = useCallback((meta) => {
    setPageMeta((prev) => {
      if (typeof meta === 'function') {
        return meta(prev);
      }
      return { ...prev, ...meta };
    });
  }, []);

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="logo" aria-label="OpenEvolve Studio">
          <span className="logo-icon">OE</span>
          <span>OpenEvolve Studio</span>
        </div>

        <nav className="nav-links">
          {NAV_LINKS.map((link) => {
            const { to, label, icon: Icon, disabled } = link;
            return (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  clsx('nav-link', { active: isActive, disabled })
                }
                aria-disabled={disabled ? 'true' : undefined}
                onClick={(event) => {
                  if (disabled) {
                    event.preventDefault();
                  }
                }}
              >
                <Icon size={18} strokeWidth={1.8} />
                <span>{label}</span>
                {disabled && (
                  <span className="badge" style={{ marginLeft: 'auto' }}>
                    Coming soon
                  </span>
                )}
              </NavLink>
            );
          })}
        </nav>
      </aside>

      <div className="main-content">
        <header className="top-bar">
          <div>
            <h1>{pageMeta.title}</h1>
            {pageMeta.subtitle && (
              <p className="card-subtitle" style={{ margin: 0 }}>
                {pageMeta.subtitle}
              </p>
            )}
          </div>
          <div>{pageMeta.actions}</div>
        </header>

        <div className="content-scroll">
          <Outlet context={{ setPageMeta: updatePageMeta }} />
        </div>
      </div>
    </div>
  );
}
